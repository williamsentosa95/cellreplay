AM_CPPFLAGS = -I$(srcdir)/../util -I$(srcdir)/../packet -I$(srcdir)/../graphing $(XCBPRESENT_CFLAGS) $(XCB_CFLAGS) $(PANGOCAIRO_CFLAGS) $(CXX11_FLAGS) -ljsoncpp
AM_CXXFLAGS = $(PICKY_CXXFLAGS)

bin_PROGRAMS = mm-cellular
mm_cellular_SOURCES = cellularshell.cc cellular_queue.hh cellular_queue.cc
mm_cellular_LDADD = -lrt ../util/libutil.a ../packet/libpacket.a ../graphing/libgraph.a $(XCBPRESENT_LIBS) $(XCB_LIBS) $(PANGOCAIRO_LIBS)
mm_cellular_LDFLAGS = -pthread

install-exec-hook:
	chown root $(DESTDIR)$(bindir)/mm-cellular
	chmod u+s $(DESTDIR)$(bindir)/mm-cellular
