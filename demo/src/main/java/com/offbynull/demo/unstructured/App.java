package com.offbynull.demo.unstructured;

import com.offbynull.overlay.visualizer.JGraphXVisualizer;
import com.offbynull.overlay.visualizer.Visualizer;
import com.offbynull.overlay.visualizer.VisualizerEventListener;
import com.offbynull.rpc.FakeTransportFactory;
import com.offbynull.rpc.transport.fake.FakeHub;
import com.offbynull.rpc.transport.fake.PerfectLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

public class App {

    public static void main(String[] args) throws Throwable {
        Visualizer<Integer> visualizer = new JGraphXVisualizer<>();
        
        FakeHub<Integer> hub = new FakeHub<>(new PerfectLine<Integer>());
        hub.start();
        
        BasicThreadFactory threadFactory = new BasicThreadFactory.Builder().daemon(true).namingPattern("Unstructured overlay thread")
                .uncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e.printStackTrace();
            }
        }).build();
        final ExecutorService executor = Executors.newFixedThreadPool(10, threadFactory);
        
        for (int i = 0; i < 10; i++) {
            executor.submit(new OverlayRunnable(i, new FakeTransportFactory<>(hub, i), i-1, 10, 10, visualizer));
        }
        
        visualizer.visualize(new VisualizerEventListener() {

            @Override
            public void closed() {
                try {
                    executor.shutdownNow();
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    // do nothing
                }
            }
        });
    }
}