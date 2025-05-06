AM_CPPFLAGS = -I$(srcdir)/../util -I$(srcdir)/../packet $(CXX11_FLAGS)
AM_CXXFLAGS = $(PICKY_CXXFLAGS)

bin_PROGRAMS = mm-cellular
mm_cellular_SOURCES = cellularshell.cc cellular_queue.hh cellular_queue.cc
mm_cellular_LDADD = -lrt ../util/libutil.a ../packet/libpacket.a
mm_cellular_LDFLAGS = -pthread

install-exec-hook:
	chown root $(DESTDIR)$(bindir)/mm-cellular
	chmod u+s $(DESTDIR)$(bindir)/mm-cellular
