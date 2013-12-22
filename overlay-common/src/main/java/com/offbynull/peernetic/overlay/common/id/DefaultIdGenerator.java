package com.offbynull.peernetic.overlay.common.id;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Random;

public final class DefaultIdGenerator implements BitLimitedIdGenerator,
        LimitedIdGenerator {

    private Random random;
    
    public DefaultIdGenerator(Random random) {
        if (random != null) {
            this.random = random;
        }
    }

    public DefaultIdGenerator() throws NoSuchAlgorithmException,
            NoSuchProviderException {
        this(SecureRandom.getInstance("SHA1PRNG", "SUN"));
    }

    @Override
    public BitLimitedId generate(int bitCount) {
        if (bitCount <= 0) {
            throw new IllegalArgumentException();
        }
        try {
            int rawLen = (bitCount / 8) + 1;
            byte[] raw = new byte[rawLen];
            random.nextBytes(raw);

            raw = BitUtils.truncateBytes(raw, bitCount);

            return new BitLimitedId(bitCount, raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public LimitedId generate(byte[] limit) {
        if (limit == null) {
            throw new IllegalArgumentException();
        }
        try {
            int rawLen = limit.length;
            byte[] raw = new byte[rawLen];
            random.nextBytes(raw);

            BigInteger rawBd = new BigInteger(1, raw);
            BigInteger limitBd = new BigInteger(1, limit);
            
            rawBd = rawBd.mod(limitBd);

            return new LimitedId(rawBd.toByteArray(), limit);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}