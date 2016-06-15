#!/usr/bin/python
from fvregress import *
import string     # really?  you have to do this?
import sys


class SwitchSocketShutdownException(FvExcept):
    def getMsg():
        return "Socket of switch was shutdown"

class FakeSwitchNoReply(FakeSwitch):

   def run(self):
        print "    Starting io loop for switch "+ self.name
        while self.alive :
            try:
                m = self.sock.recv(FakeSwitch.BUFSIZE)
                if m == '' :
                    if self.alive:
                        print "Switch " + self.name + " got EOF ; exiting..."
                        self.alive=False
                        raise SwitchSocketShutdownException()
                    return
            except (Exception), e :
                if self.alive:
                    print "Switch " + self.name + " got " + str(e) + "; exiting..."
                return
            #print "----------------- got packet"
            self.msg_cond.acquire()
            msgs = self.of_frame(m)
            for m in msgs :
                self.msgs.append(m)
                self.msg_cond.notify()
            self.msg_cond.release()

class NoFVHelloResponseException(FvExcept):
    def getMsg():
        return "No Hello response received from FlowVisor"

def initiate_connection(name="", sw=None):
    if sw == None:
        sw = FakeSwitch(name,host='localhost', port=port)
        sw.start()
    print "    Switch " + sw.name + " sent hello"

    sw.send(a2b(FvRegress.HELLO))

    m = sw.recv_blocking(timeout=3)
    if m == None:
        raise NoFVHelloResponseException()

    if not of_cmp(b2a(m),FvRegress.HELLO) :
        of_diff(b2a(m), FvRegress.HELLO)
        raise FvExcept("Failed to get hello from flowvisor for switch " + sw.name + " got "+ b2a(m))
    print "    addSwitch: Got hello flush for " + sw.name

    m = sw.recv_blocking(timeout=3)
    if not of_cmp(b2a(m),FvRegress.FLOW_MOD_FLUSH) :
        of_diff(b2a(m), FvRegress.FLOW_MOD_FLUSH)
        raise FvExcept("Failed to get a flow_mod flush from flowvisor for switch " + sw.name + " got " + b2a(m))
    print "    addSwitch: Got flow_mod flush for " + sw.name
    m = sw.recv_blocking(timeout=3)
    if not of_cmp(b2a(m),FvRegress.FEATURE_REQUEST) :
        of_diff(b2a(m), FvRegress.FEATURE_REQUEST)
        raise FvExcept("Failed to get features_request from flowvisor for switch " + sw.name + " got " + b2a(m))
    print "    addSwitch: Got feature_request (from FV) for " + sw.name

    return (m, sw)


unique_dpid = 1
def complete_handshake(h, feature_request, switch):
    switch_features = h.make_feature_reply(dpid=unique_dpid, nPorts=1)
    unique_dipd = unique_dpid + 1

    switch_features = switch_features[0:4] + feature_request[4:8] + switch_features[8:]
    switch.send(switch_features)


# start up a flowvisor with 1 switch (default) and two guests

wantPause = True

try:

    h= FvRegress()
    port=16633
    #h.addController("alice",    54321)
    #h.addController("bob",      54322)

    if len(sys.argv) > 1 :
        wantPause = False
        port=int(sys.argv[1])
        timeout=60
        h.useAlreadyRunningFlowVisor(port)
    else:
        wantPause = False
        timeout=5
        h.spawnFlowVisor(configFile="tests-stats.xml")
    h.lamePause()


    if wantPause:
        doPause("start tests")
##################################
    # Create switch and contact controller, but don't send feature reply

    (feature_request, switch1) = initiate_connection('switch1')
    print "################################# Starting Test 1: Attempting to send Hello after no fvreply ########"
    exception = None
    try:
       initiate_connection(sw=switch1)
    except NoFVHelloResponseException as e:
        exception = e

    if e == None:
        print "######################## Test 1 FAILED!!! ##########################" 
        raise FvExcept("Test 1 in no-reply Failed")

    print "######################## Starting Test2: Pause for 5s and wait to see if socket is shutdown"
    exception = None
    try:
        h.lamePause(msg="Pausing 5s to allow FV to timeout waiting for feature response and tear down connection", pause=5.0)
    except SwitchSocketShutdownException as e:
        exception = e

    if e == None:
        print "########################### Test 2 Failed!!! #############################"
        raise FvExcept("Test 2 in no-reply failed")

    print "############################ Starting Test 3: Complete Handshake ###########################"
    (feature_request, switch2) = initiate_connection('switch2')
    complete_handshake(h, feature_request, switch2)
    h.lamePause(msg="Pause 5s to ensure FV does not close socket", pause=5.0)


#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
        doPause("start cleanup")
    h.cleanup()

