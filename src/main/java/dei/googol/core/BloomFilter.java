package dei.googol.core;

import java.util.BitSet;
import java.util.Random;

public class BloomFilter {
    private BitSet bitSet;
    private int size;
    private int numHashFunctions;
    private Random random = new Random();

    public BloomFilter(int size, int numHashFunctions) {
        this.size = size;
        this.numHashFunctions = numHashFunctions;
        this.bitSet = new BitSet(size);
    }

    public void add(String element) {
        for (int i = 0; i < numHashFunctions; i++) {
            int hash = hash(element, i);
            bitSet.set(hash, true);
        }
    }

    public boolean mightContain(String element) {
        for (int i = 0; i < numHashFunctions; i++) {
            int hash = hash(element, i);
            if (!bitSet.get(hash)) {
                return false;
            }
        }
        return true;
    }

    private int hash(String element, int seed) {
        random.setSeed(seed);
        return Math.abs((element.hashCode() ^ random.nextInt()) % size);
    }
}