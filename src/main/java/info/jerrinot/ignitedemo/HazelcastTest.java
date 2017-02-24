package info.jerrinot.ignitedemo;

import com.hazelcast.config.Config;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.NativeMemoryConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.map.AbstractEntryProcessor;
import com.hazelcast.memory.MemorySize;
import com.hazelcast.memory.MemoryUnit;
import com.hazelcast.nio.serialization.Portable;
import com.hazelcast.nio.serialization.PortableFactory;
import com.hazelcast.nio.serialization.PortableReader;
import com.hazelcast.nio.serialization.PortableWriter;
import com.hazelcast.spi.properties.GroupProperty;
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

import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.enterprise.SampleLicense.UNLIMITED_LICENSE;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 30, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 800, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
public class HazelcastTest {
    private static final int PORTABLE_FACTORY_ID = 123;
    private static final int PORTABLE_CLASS_ID = 123;
    private static final long OBJECT_BYTE_SIZE = MemoryUnit.KILOBYTES.toBytes(130);
    private IMap<String, Object> offheapMap;
    private HazelcastInstance instance;
    private IMap<Object, Object> binaryMap;

    protected Config getConfig() {
        return nativeConfig();
    }

    private Config nativeConfig() {
        final Config c = new Config();
        final MapConfig offheapMapConfig = new MapConfig()
                .setName("offheapMap")
                .setInMemoryFormat(InMemoryFormat.NATIVE);
        final MapConfig binaryMapConfig = new MapConfig()
                .setName("binaryMap")
                .setInMemoryFormat(InMemoryFormat.BINARY);
        final NativeMemoryConfig memoryConfig = new NativeMemoryConfig()
                .setEnabled(true)
                .setSize(new MemorySize(2, MemoryUnit.GIGABYTES))
                .setAllocatorType(NativeMemoryConfig.MemoryAllocatorType.POOLED);
        c.getSerializationConfig()
                .addPortableFactory(PORTABLE_FACTORY_ID, new PortableFactory() {
                    public Portable create(int classId) {
                        return new OffHeapObject();
                    }
                });
        return c.addMapConfig(offheapMapConfig)
                .addMapConfig(binaryMapConfig)
                .setNativeMemoryConfig(memoryConfig);
    }

    @Setup
    public void setup() throws IOException {
        GroupProperty.ENTERPRISE_LICENSE_KEY.setSystemProperty(UNLIMITED_LICENSE);
        final Config config = getConfig();
        instance = Hazelcast.newHazelcastInstance(config);
        this.offheapMap = instance.getMap("offheapMap");
        this.binaryMap = instance.getMap("binaryMap");
        final Random random = new Random();
        offheapMap.put("1", randomOffHeapObject(random));
        binaryMap.put("1", randomOffHeapObject(random));
    }

    @Benchmark
    public Object offheapEP() throws IOException {
        return offheapMap.executeOnKey("1", new AbstractEntryProcessor<Integer, OffHeapObject>() {
            @Override
            public Object process(Map.Entry<Integer, OffHeapObject> entry) {
                final byte[] bytes = entry.getValue().bytes;
                bytes[100] = 1;
                return null;
            }
        });
    }

    @Benchmark
    public Object binaryEP() throws IOException {
        return binaryMap.executeOnKey("1", new AbstractEntryProcessor<Integer, OffHeapObject>() {
            @Override
            public Object process(Map.Entry<Integer, OffHeapObject> entry) {
                final byte[] bytes = entry.getValue().bytes;
                bytes[100] = 1;
                return null;
            }
        });
    }

    @TearDown
    public void tearDown() throws IOException {
        instance.shutdown();
    }

    private OffHeapObject randomOffHeapObject(Random random) {
        final byte[] bytes = new byte[(int) OBJECT_BYTE_SIZE];
        random.nextBytes(bytes);
        return new OffHeapObject(bytes);
    }

    public static class OffHeapObject implements Portable {
        public byte[] bytes;

        public OffHeapObject() {
        }

        public OffHeapObject(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int getFactoryId() {
            return PORTABLE_FACTORY_ID;
        }

        @Override
        public int getClassId() {
            return PORTABLE_CLASS_ID;
        }

        @Override
        public void writePortable(PortableWriter writer) throws IOException {
            writer.writeByteArray("bytes", bytes);
        }

        @Override
        public void readPortable(PortableReader reader) throws IOException {
            bytes = reader.readByteArray("bytes");
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(HazelcastTest.class.getSimpleName())
                .verbosity(VerboseMode.NORMAL)
                .threads(4)
                .build();
        new Runner(opt).run();
    }
}