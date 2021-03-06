package com.offbynull.peernetic.debug.actornetwork;

import com.offbynull.peernetic.debug.actornetwork.messages.TransitMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.apache.commons.lang3.Validate;

public final class SimpleLine<A> implements Line<A> {

    private Random random;
    private Duration minDelayPerKb;
    private Duration maxJitterPerKb;
    private double dropChancePerKb;
    private double nonRepeatChancePerKb;
    private int maxRepeat;

    public SimpleLine() {
        this(0L, Duration.ZERO, Duration.ZERO, 0.0, 1.0, 0); // no delay, no jitter, no dropped packets, no repeating packets
    }

    public SimpleLine(long randomSeed, Duration minDelayPerKb, Duration maxJitterPerKb, double dropChancePerKb, double nonRepeatChancePerKb,
            int maxRepeat) {
        Validate.notNull(minDelayPerKb);
        Validate.notNull(maxJitterPerKb);
        Validate.isTrue(!minDelayPerKb.isNegative() && !maxJitterPerKb.isNegative()
                && dropChancePerKb >= 0.0 && nonRepeatChancePerKb >= 0.0
                && dropChancePerKb <= 1.0 && nonRepeatChancePerKb <= 1.0
                && maxRepeat >= 0);
        this.random = new Random(randomSeed);
        this.minDelayPerKb = minDelayPerKb;
        this.maxJitterPerKb = maxJitterPerKb;
        this.dropChancePerKb = dropChancePerKb; // chance * kb = how likely it'll drop
        // so 0.1 * 10kb = 1.0 -- when means it'll get dropped 100% of the time
        this.nonRepeatChancePerKb = nonRepeatChancePerKb; // 1 - (chance * kb) = how likely it WON'T repeat
        // so 1 - (0.1 * 10kb) = 1 - 1.0 = 0 -- which means it'll never repeat once size >= 10kb
        // repeat rate = rate/kb = 0.1rate/10kb = 0.01 repeat count = 0
        // repeat rate = rate/kb = 0.1rate/1kb = 1 repeat count = 1
        this.maxRepeat = maxRepeat;
    }

    @Override
    public void nodeJoin(A address) {
        // do nothing
    }

    @Override
    public void nodeLeave(A address) {
        // do nothing
    }

    @Override
    public Collection<TransitMessage<A>> messageDepart(Instant time, BufferMessage<A> departMessage) {
        int size = departMessage.getData().remaining();
        int sizeInKb = size / 1024 + (size % 1024 == 0 ? 0 : 1); // to kb, always round up

        List<TransitMessage<A>> ret = new ArrayList<>();
        int repeatCount = 0;
        do {
            if (calculateNextDrop(sizeInKb)) {
                continue;
            }

            Duration delay = calculateNextDuration(sizeInKb);
            TransitMessage<A> transitMsg = new TransitMessage<>(departMessage.getSource(), departMessage.getDestination(),
                    departMessage.getData(), time, delay);
            ret.add(transitMsg);
            
            repeatCount++;
        } while (repeatCount <= maxRepeat && calculateNextRepeat(sizeInKb));
        
        return ret;
    }

    @Override
    public Collection<BufferMessage<A>> messageArrive(Instant time, TransitMessage<A> transitMessage) {
        BufferMessage<A> bufferMessage = new BufferMessage<>(transitMessage.getData(), transitMessage.getSource(),
                transitMessage.getDestination());
        return Collections.singleton(bufferMessage);
    }

    private Duration calculateNextDuration(int sizeInKb) {
        long maxJitterMillis = maxJitterPerKb.toMillis();
        long jitter = maxJitterMillis == 0 ? 0L : random.nextLong() % (maxJitterMillis * (long) sizeInKb);
        
        Duration nextDuration = minDelayPerKb.multipliedBy(sizeInKb).plusMillis(jitter);
        return nextDuration.isNegative() ? Duration.ZERO : nextDuration; // if neg, give back 0
    }

    private boolean calculateNextRepeat(int sizeInKb) {
        return random.nextDouble() > (nonRepeatChancePerKb * sizeInKb);
    }

    private boolean calculateNextDrop(int sizeInKb) {
        return random.nextDouble() <= (dropChancePerKb * sizeInKb);
    }
}
