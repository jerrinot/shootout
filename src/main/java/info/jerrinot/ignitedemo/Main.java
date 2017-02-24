package info.jerrinot.ignitedemo;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;

public class Main {
    public static void main(String[] args) {
        Ignite start = Ignition.start("ignite.xml");
        IgniteCache<Integer, String> offheapCache = start.getOrCreateCache("offheapCache");
        offheapCache.put(0, "foo");
    }

}
