package com.offbynull.rpc;

import com.offbynull.rpc.invoke.AsyncResultListener;
import com.offbynull.rpc.transport.CompositeIncomingFilter;
import com.offbynull.rpc.transport.CompositeIncomingMessageListener;
import com.offbynull.rpc.transport.CompositeOutgoingFilter;
import com.offbynull.rpc.transport.IncomingFilter;
import com.offbynull.rpc.transport.IncomingMessageListener;
import com.offbynull.rpc.transport.OutgoingFilter;
import com.offbynull.rpc.transport.Transport;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.Validate;

/**
 * RPC entry point.
 * @author Kasra F
 * @param <A> address type
 */
public final class Rpc<A> implements Closeable {

    private boolean closed;

    private Transport<A> transport;

    private ServiceRouter<A> serviceRouter;
    private ServiceAccessor<A> serviceAccessor;
    
    /**
     * Constructs an RPC object.
     * @param transportFactory transport factory
     * @throws IOException on error
     * @throws NullPointerException if any arguments is {@code null}
     */
    public Rpc(TransportFactory<A> transportFactory) throws IOException {
        this(transportFactory, new RpcConfig<A>());
    }

    /**
     * Constructs an RPC object.
     * @param transportFactory transport factory
     * @param conf configuration
     * @throws IOException on error
     * @throws NullPointerException if any arguments is {@code null}
     */
    public Rpc(TransportFactory<A> transportFactory, RpcConfig<A> conf) throws IOException {
        Validate.notNull(conf);
        Validate.notNull(transportFactory);

        try {
            transport = transportFactory.createTransport();

            serviceRouter = new ServiceRouter<>(conf.getInvokerExecutorService(), this, conf.getExtraInvokeInfo());
            serviceAccessor = new ServiceAccessor<>(transport);

            List<IncomingMessageListener<A>> incomingMessageListeners = new ArrayList<>();
            incomingMessageListeners.addAll(conf.getPreIncomingMessageListeners());
            incomingMessageListeners.add(serviceRouter.getIncomingMessageListener());
            incomingMessageListeners.addAll(conf.getPostIncomingMessageListeners());
            
            IncomingMessageListener<A> listener = new CompositeIncomingMessageListener<>(incomingMessageListeners);
            IncomingFilter<A> inFilter = new CompositeIncomingFilter<>(conf.getIncomingFilters());
            OutgoingFilter<A> outFilter = new CompositeOutgoingFilter<>(conf.getOutgoingFilters());
            transport.start(inFilter, listener, outFilter);
            
            closed = false;
        } catch (IOException | RuntimeException ex) {
            closed = true;
            close();
            throw ex;
        }
    }

    @Override
    public void close() {
        closed = true;

        try {
            transport.stop();
        } catch (Exception ex) {
            // do nothing
        }
    }

    /**
     * Add a service.
     * @param id service id
     * @param object service object
     * @throws IllegalStateException if closed
     * @throws IllegalArgumentException if {@code id} is 0
     * @throws NullPointerException if {@code object} is {@code null}
     */
    public void addService(int id, Object object) {
        Validate.validState(!closed);

        serviceRouter.addService(id, object);
    }

    /**
     * Remove a service.
     * @param id service id
     * @throws IllegalStateException if closed
     * @throws IllegalArgumentException if {@code id} is 0
     */
    public void removeService(int id) {
        Validate.validState(!closed);

        serviceRouter.removeService(id);
    }

    /**
     * Access a remote service asynchronously. Throws a {@link RuntimeException} on communication/invokation failure. Must not block in
     * {@link AsyncResultListener}.
     * @param address destination address
     * @param id service id
     * @param type service type class
     * @param asyncType service async type class
     * @param <T> service type
     * @param <AT> asynchronous service type
     * @return an object of type {@code type} that accesses the service at {@code id}
     * @throws IllegalStateException if closed
     * @throws NullPointerException if any arguments are {@code null}
     */
    public <T, AT> AT accessService(A address, int id, Class<T> type, Class<AT> asyncType) {
        Validate.validState(!closed);

        return serviceAccessor.accessServiceAsync(address, id, type, asyncType, new RuntimeException("Comm failure"),
                new RuntimeException("Invoke failure"));
    }

    /**
     * Access a remote service asynchronously. Must not block in {@link AsyncResultListener}.
     * @param address destination address
     * @param id service id
     * @param type service type class
     * @param asyncType service async type class
     * @param throwOnCommFailure exception to throw on communication failure
     * @param throwOnInvokeFailure exception to throw on invokation failure
     * @param <T> service type
     * @param <AT> asynchronous service type
     * @return an object of type {@code type} that accesses the service at {@code id}
     * @throws IllegalStateException if closed
     * @throws IllegalArgumentException if {@code timeout < 1L} 
     * @throws NullPointerException if any arguments are {@code null}
     */
    public <T, AT> AT accessService(A address, int id, Class<T> type, Class<AT> asyncType, RuntimeException throwOnCommFailure,
            RuntimeException throwOnInvokeFailure) {
        Validate.validState(!closed);

        return serviceAccessor.accessServiceAsync(address, id, type, asyncType, throwOnCommFailure, throwOnInvokeFailure);
    }

    /**
     * Access a remote service. Times out after 10 seconds. Throws a {@link RuntimeException} on communication/invokation failure.
     * @param address destination address
     * @param id service id
     * @param type service type class
     * @param <T> service type
     * @return an object of type {@code type} that accesses the service at {@code id}
     * @throws IllegalStateException if closed
     * @throws NullPointerException if any arguments are {@code null}
     */
    public <T> T accessService(A address, int id, Class<T> type) {
        Validate.validState(!closed);

        return serviceAccessor.accessService(address, id, type, new RuntimeException("Comm failure"),
                new RuntimeException("Invoke failure"));
    }

    /**
     * Access a remote service.
     * @param address destination address
     * @param id service id
     * @param type service type class
     * @param throwOnCommFailure exception to throw on communication failure
     * @param throwOnInvokeFailure exception to throw on invokation failure
     * @param <T> service type
     * @return an object of type {@code type} that accesses the service at {@code id}
     * @throws IllegalStateException if closed
     * @throws IllegalArgumentException if {@code timeout < 1L} 
     * @throws NullPointerException if any arguments are {@code null}
     */
    public <T> T accessService(A address, int id, Class<T> type, RuntimeException throwOnCommFailure,
            RuntimeException throwOnInvokeFailure) {
        Validate.validState(!closed);

        return serviceAccessor.accessService(address, id, type, throwOnCommFailure, throwOnInvokeFailure);
    }
}
