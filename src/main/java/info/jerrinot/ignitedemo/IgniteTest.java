package info.jerrinot.ignitedemo;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.MutableEntry;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 30, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 800, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
public class IgniteTest {
    private static final long OBJECT_BYTE_SIZE = 130 * 1024;
    private IgniteCache<String, OffHeapObject> offheapMap;
    private Ignite instance;
    private IgniteCache<String, OffHeapObject> binaryMap;


    @Setup
    public void setup() throws IOException {
        instance = Ignition.start("ignite.xml");
        this.offheapMap = instance.getOrCreateCache("offheapMap");
        this.binaryMap = instance.getOrCreateCache("binaryMap");
        final Random random = new Random();
        offheapMap.put("1", randomOffHeapObject(random));
        binaryMap.put("1", randomOffHeapObject(random));
    }

    @Benchmark
    public Object offheapEP() throws IOException {
        return offheapMap.invoke("1", new EntryProcessor<String, OffHeapObject, Object>() {
            @Override
            public Object process(MutableEntry<String, OffHeapObject> entry, Object... arguments) throws EntryProcessorException {
                final byte[] bytes = entry.getValue().bytes;
                bytes[100] = 1;
                return null;
            }
        });
    }

    @Benchmark
    public Object binaryEP() throws IOException {
        return binaryMap.invoke("1", new EntryProcessor<String, OffHeapObject, Object>() {
            @Override
            public Object process(MutableEntry<String, OffHeapObject> entry, Object... arguments) throws EntryProcessorException {
                final byte[] bytes = entry.getValue().bytes;
                bytes[100] = 1;
                return null;
            }
        });
    }

    @TearDown
    public void tearDown() throws IOException {
        instance.close();
    }

    private OffHeapObject randomOffHeapObject(Random random) {
        final byte[] bytes = new byte[(int) OBJECT_BYTE_SIZE];
        random.nextBytes(bytes);
        return new OffHeapObject(bytes);
    }

    public static class OffHeapObject {
        public byte[] bytes;

        public OffHeapObject() {
        }

        public OffHeapObject(byte[] bytes) {
            this.bytes = bytes;
        }

    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(IgniteTest.class.getSimpleName())
                .verbosity(VerboseMode.NORMAL)
                .threads(4)
                .build();
        new Runner(opt).run();
    }
}