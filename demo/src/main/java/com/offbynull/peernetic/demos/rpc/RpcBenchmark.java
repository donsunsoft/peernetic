package com.offbynull.peernetic.demos.rpc;

import com.offbynull.peernetic.rpc.FakeTransportFactory;
import com.offbynull.peernetic.rpc.Rpc;
import com.offbynull.peernetic.rpc.common.services.ping.PingService;
import com.offbynull.peernetic.rpc.common.services.ping.PingServiceAsync;
import com.offbynull.peernetic.rpc.common.services.ping.PingServiceImplementation;
import com.offbynull.peernetic.rpc.invoke.AsyncResultListener;
import com.offbynull.peernetic.rpc.transport.fake.FakeHub;
import com.offbynull.peernetic.rpc.transport.fake.PerfectLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RpcBenchmark {
    private static final int NUM_OF_TRANSPORTS = 100;
    private static FakeHub<Integer> fakeHub = new FakeHub<>(new PerfectLine<Integer>());
    private static List<Client> clients = new ArrayList<>();
    
    public static void main(String[] args) throws Throwable {
        fakeHub.start();

        for (int i = 0; i < NUM_OF_TRANSPORTS; i++) {
            FakeTransportFactory<Integer> transportFactory = new FakeTransportFactory(fakeHub, i);
            Rpc<Integer> rpc = new Rpc(transportFactory);
            
            rpc.addService(PingService.SERVICE_ID, new PingServiceImplementation());
            
            // Pre-populate accessors, because creating an accessor each time you need to make a call will be super slow.
            List<PingServiceAsync> accessors = populateRpcAccessClasses(i, rpc);
            
            clients.add(new Client(rpc, accessors));
        }

        for (int i = 0; i < NUM_OF_TRANSPORTS; i++) {
            issueMessage(i, (i + 1) % NUM_OF_TRANSPORTS);
        }
    }
    
    private static List<PingServiceAsync> populateRpcAccessClasses(int address, Rpc<Integer> rpc) {
        List<PingServiceAsync> ret = new ArrayList<>(NUM_OF_TRANSPORTS);
                
        for (int i = 0; i < NUM_OF_TRANSPORTS; i++) {
            int to = (address + i) % NUM_OF_TRANSPORTS;
            PingServiceAsync serviceAsync = rpc.accessService(to, PingService.SERVICE_ID, PingService.class, PingServiceAsync.class);
            ret.add(serviceAsync);
        }
        
        return ret;
    }

    private static void issueMessage(int from, int to) {
        final long time = System.currentTimeMillis();

        Client client = clients.get(from);
        PingServiceAsync serviceAsync = client.getServices().get(to);
        
        serviceAsync.ping(new ResultListener(from, to), time);
    }

    private static final class Client {
        private Rpc<Integer> rpc;
        private List<PingServiceAsync> services;

        public Client(Rpc<Integer> rpc, List<PingServiceAsync> services) {
            this.rpc = rpc;
            this.services = Collections.unmodifiableList(services);
        }

        public Rpc<Integer> getRpc() {
            return rpc;
        }

        public List<PingServiceAsync> getServices() {
            return services;
        }
        
    }
    
    private static final class ResultListener implements AsyncResultListener<Long> {
        private int from;
        private int to;

        public ResultListener(int from, int to) {
            this.from = from;
            this.to = to;
        }
        
        @Override
        public void invokationReturned(Long object) {
            long diff = System.currentTimeMillis() - object;
            System.out.println("Response time: " + diff);
            
            issueMessage(from, to);
        }

        @Override
        public void invokationThrew(Throwable err) {
            System.err.println("THREW: " + err);
        }

        @Override
        public void invokationFailed(Object err) {
            System.err.println("FAILED");
        }
        
    }

}