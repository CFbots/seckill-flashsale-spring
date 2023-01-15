package com.jiuzhang.seckill.util;

public class SnowFlake {
    // start time
    private final static long START_STAMP = 1480166465631L;

    // bit used by each part
    private final static long SEQUENCE_BIT = 12;
    private final static long MACHINE_BIT = 5;
    private final static long DATACENTER_BIT = 5;

    // max value for each bit
    private final static long MAX_DATACENTER_NUM = -1L ^ (-1L << DATACENTER_BIT);
    private final static long MAX_MACHINE_NUM = -1L ^ (-1L << MACHINE_BIT);
    private final static long MAX_SEQUENCE = -1L ^ (-1L << SEQUENCE_BIT);

    // left bit shift for each part
    private final static long MACHINE_LEFT = SEQUENCE_BIT;
    private final static long DATACENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
    private final static long TIMESTMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;

    private long datacenterId;
    private long machineId;
    private long sequence = 0L;
    private long lastStmp = -1L;

    public SnowFlake(long datacenterId, long machineId) {
        if (datacenterId > MAX_DATACENTER_NUM || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId cannot be greater than MAX_DATACENTER_NUM or less than 0");
        }
        if (machineId > MAX_MACHINE_NUM || machineId < 0) {
            throw new IllegalArgumentException("machineId cannot be greater than MAX_MACHINE_NUM or less than 0");
        }

        this.datacenterId = datacenterId;
        this.machineId = machineId;
    }

    public synchronized long nextId() {
        long currStmp = getNewStmp();
        if (currStmp < lastStmp) {
            throw new RuntimeException("Cloud moved backwards. Refusing to generate id.");
        }

        if (currStmp == lastStmp) {
            // increment sequence number for same time stamp
            sequence = (sequence + 1) & MAX_SEQUENCE;

            // time stamp reaches maximum
            if (sequence == 0L) {
                currStmp = getNextMill();
            } else {
                // within different mill, set sequence number to 0
                sequence = 0L;
            }
        }

        lastStmp = currStmp;

        return (currStmp - START_STAMP) << TIMESTMP_LEFT
                | datacenterId << DATACENTER_LEFT
                | machineId << MACHINE_LEFT
                | sequence;
    }

    private long getNewStmp() {
        return System.currentTimeMillis();
    }

    private long getNextMill() {
        long mill = getNewStmp();
        while (mill <= lastStmp) {
            mill = getNewStmp();
        }
        return mill;
    }

    public static void main(String[] args) {
        SnowFlake snowFlake = new SnowFlake(2, 1);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            System.out.println(snowFlake.nextId());
        }

        System.out.println(String.format("Total time elapsed: %s",
                (System.currentTimeMillis() - start)));
    }

}
