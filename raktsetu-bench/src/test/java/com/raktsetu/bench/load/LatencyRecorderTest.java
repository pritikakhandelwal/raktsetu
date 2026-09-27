package com.raktsetu.bench.load;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class LatencyRecorderTest {

    @Test
    void percentilesUseNearestRank() {
        long[] ms = IntStream.rangeClosed(1, 100).mapToLong(i -> i * 1_000_000L).toArray();
        assertThat(LatencyRecorder.percentile(ms, 50)).isEqualTo(50.0);
        assertThat(LatencyRecorder.percentile(ms, 95)).isEqualTo(95.0);
        assertThat(LatencyRecorder.percentile(ms, 99)).isEqualTo(99.0);
        assertThat(LatencyRecorder.percentile(new long[0], 99)).isZero();
    }

    @Test
    void summarisesPerOperationAndOverall() throws InterruptedException {
        LatencyRecorder recorder = new LatencyRecorder();
        Thread[] threads = new Thread[4];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = Thread.ofVirtual().start(() -> {
                for (int i = 1; i <= 250; i++) {
                    recorder.record(i % 2 == 0 ? "read" : "write", i * 1_000_000L, i != 7);
                }
            });
        }
        for (Thread t : threads) {
            t.join();
        }
        assertThat(recorder.totalCalls()).isEqualTo(1000);
        assertThat(recorder.totalErrors()).isEqualTo(4);
        var summary = recorder.summary();
        assertThat(summary).extracting(LatencyRecorder.Stats::operation).containsExactly("read", "write", "all");
        assertThat(summary.getLast().calls()).isEqualTo(1000);
        assertThat(summary.getLast().max()).isEqualTo(250.0);
    }

    @Test
    void optionsHaveSensibleDefaults() {
        var o = LoadTest.parse(new String[] {"--levels", "5, 10", "--seconds", "3"});
        assertThat(o.levels()).containsExactly(5, 10);
        assertThat(o.duration().toSeconds()).isEqualTo(3);
        assertThat(o.url().getPort()).isEqualTo(8090);
        assertThat(o.out()).isNull();
    }
}
