/* -*-mode:c++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */

#include <getopt.h>

#include "infinite_packet_queue.hh"
#include "drop_tail_packet_queue.hh"
#include "drop_head_packet_queue.hh"
#include "cellular_queue.hh"
#include "packetshell.cc"
#include "ezio.hh"

using namespace std;

void usage_error( const string & program_name )
{
    cerr << "Usage: " << program_name << " NUM_ARGS UP-PACKET-TRAIN-TRACE DOWN-PACKET-TRAIN-TRACE UP-PDO DOWN-PDO PACKET-LOG-PATH-PREFIX [OPTION]... [COMMAND]" << endl;
    cerr << endl;
    cerr << "Options = --once" << endl;
    cerr << "          --drx-trace=DRX_TRACE_PATH" << endl;
    cerr << "          --loss-rate=LOSS_RATE" << endl;
    cerr << "          --psize-latency-offset-up=path" << endl;
    cerr << "          --psize-latency-offset-down=path" << endl;
    cerr << "          --uplink-log=FILENAME --downlink-log=FILENAME" << endl;
    cerr << "          --start-timestamp=TIMESTAMP_MS" << endl;
    cerr << "          --meter-uplink --meter-uplink-delay" << endl;
    cerr << "          --meter-downlink --meter-downlink-delay" << endl;
    cerr << "          --meter-all" << endl;
    cerr << "          --uplink-queue=QUEUE_TYPE --downlink-queue=QUEUE_TYPE" << endl;
    cerr << "          --uplink-queue-args=QUEUE_ARGS --downlink-queue-args=QUEUE_ARGS" << endl;
    cerr << endl;
    cerr << "          QUEUE_TYPE = infinite | droptail | drophead" << endl;
    cerr << "          QUEUE_ARGS = \"NAME=NUMBER[, NAME2=NUMBER2, ...]\"" << endl;
    cerr << "              (with NAME = bytes | packets)" << endl << endl;

    throw runtime_error( "invalid arguments" );
}

unique_ptr<AbstractPacketQueue> get_packet_queue( const string & type, const string & args, const string & program_name )
{
    if ( type == "infinite" ) {
        return unique_ptr<AbstractPacketQueue>( new InfinitePacketQueue( args ) );
    } else if ( type == "droptail" ) {
        return unique_ptr<AbstractPacketQueue>( new DropTailPacketQueue( args ) );
    } else if ( type == "drophead" ) {
        return unique_ptr<AbstractPacketQueue>( new DropHeadPacketQueue( args ) );
    } else {
        cerr << "Unknown queue type: " << type << endl;
    }

    usage_error( program_name );

    return nullptr;
}

string shell_quote( const string & arg )
{
    string ret = "'";
    for ( const auto & ch : arg ) {
        if ( ch != '\'' ) {
            ret.push_back( ch );
        } else {
            ret += "'\\''";
        }
    }
    ret += "'";

    return ret;
}

int main( int argc, char *argv[] )
{
    try {
        /* clear environment while running as root */
        char ** const user_environment = environ;
        environ = nullptr;

        check_requirements( argc, argv );

        if ( argc < 5 ) {
            usage_error( argv[ 0 ] );
        }

        string command_line { shell_quote( argv[ 0 ] ) }; /* for the log file */
        for ( int i = 1; i < argc; i++ ) {
            command_line += string( " " ) + shell_quote( argv[ i ] );
        }

        const int num_args = atoi(argv[1]);
        vector<string> extra_cmd;
        if (argc > num_args) {
            for (int i = num_args  ; i < argc; i++) {
                extra_cmd.push_back(argv[i]);    
            }
        }

        const option command_line_options[] = {
            { "once",                       no_argument, nullptr, 'o' },
            { "packet-log-folder",    required_argument, nullptr, 'i' },
            { "uplink-queue",         required_argument, nullptr, 'q' },
            { "downlink-queue",       required_argument, nullptr, 'w' },
            { "uplink-queue-args",    required_argument, nullptr, 'a' },
            { "downlink-queue-args",  required_argument, nullptr, 'b' },
            { "start-timestamp",    required_argument, nullptr, 'c' },
            { "loss-rate",                  required_argument, nullptr, 'r' },
            { "psize-latency-offset-up",                  required_argument, nullptr, 'e' },
            { "psize-latency-offset-down",                  required_argument, nullptr, 'f' },
            { "long-to-short-timer-up",                  required_argument, nullptr, 'g' },
            { "long-to-short-timer-down",                  required_argument, nullptr, 'h' },
            { 0,                                      0, nullptr, 0 },
        };

        bool repeat = true;
        string uplink_queue_type = "infinite", downlink_queue_type = "infinite",
               uplink_queue_args, downlink_queue_args;

        uint64_t start_timestamp = 0;
        double loss_rate = 0;
        string psize_offset_up = "";
        string psize_offset_down = "";
        int long_to_short_timer_up = 0;
        int long_to_short_timer_down = 0;
        string packet_log_prefix = "";

        while ( true ) {
            const int opt = getopt_long( argc, argv, "u:d:", command_line_options, nullptr );
            if ( opt == -1 ) { /* end of options */
                break;
            }

            switch ( opt ) {
            case 'c':
                start_timestamp = static_cast<uint64_t>(stoul(optarg));
                cout << "Cellular shell start_timestamp = " << start_timestamp << endl;
                break;
            case 'i':
                packet_log_prefix = optarg;
                break;
            case 'o':
                repeat = false;
                break;
            case 'q':
                uplink_queue_type = optarg; 
                break;
            case 'w':
                downlink_queue_type = optarg;
                break;
            case 'a':
                uplink_queue_args = optarg;
                break;
            case 'b':
                downlink_queue_args = optarg;
                break;
            case 'r':
                loss_rate = static_cast<double>(stod(optarg));
                break;
            case 'e':
                psize_offset_up = optarg;
                break;
            case 'f':
                psize_offset_down = optarg;
                break;
            case 'g':
                long_to_short_timer_up = myatoi(optarg);
                break;
            case 'h':
                long_to_short_timer_down = myatoi(optarg);
                break;
            case '?':
                break;
            }
        }

        if ( optind + 1 >= argc ) {
            usage_error( argv[ 0 ] );
        }

        const string up_packet_train_trace = argv[ optind + 1];
        const string down_packet_train_trace = argv[ optind + 2];
        const string uplink_pdo_trace = argv[ optind + 3 ];
        const string downlink_pdo_trace = argv[ optind + 4 ];

        vector<string> command; 

        if ( extra_cmd.size() == 0 ) {
            command.push_back( shell_path() );
        } else {
            for ( unsigned int i = 0; i < extra_cmd.size(); i++ ) {
                command.push_back( extra_cmd[ i ] );
            }
        }

        PacketShell<CellularQueue> cellular_shell_app( "cell-link", user_environment );

        cellular_shell_app.start_uplink( "[cell-link] ", command,
                                     "Uplink", up_packet_train_trace, uplink_pdo_trace, packet_log_prefix, repeat, 
                                     get_packet_queue( uplink_queue_type, uplink_queue_args, argv[ 0 ] ),
                                     true, start_timestamp, loss_rate, psize_offset_up, long_to_short_timer_up,
                                     command_line );

        cellular_shell_app.start_downlink( "Downlink", down_packet_train_trace, downlink_pdo_trace, packet_log_prefix, repeat, 
                                       get_packet_queue( downlink_queue_type, downlink_queue_args, argv[ 0 ] ), 
                                       false, start_timestamp, loss_rate, psize_offset_down, long_to_short_timer_down,
                                       command_line );

        return cellular_shell_app.wait_for_exit();
    } catch ( const exception & e ) {
        print_exception( e );
        return EXIT_FAILURE;
    }
}
