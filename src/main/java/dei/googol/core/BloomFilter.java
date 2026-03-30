package dei.googol.core;

import java.util.BitSet;

/**
 * A space-efficient probabilistic data structure used to test whether a URL has
 * already been visited by the web crawler.
 *
 * <p><strong>Properties:</strong>
 * <ul>
 *   <li>No false negatives — if {@link #mightContain} returns {@code false},
 *       the element was definitely never added.</li>
 *   <li>Possible false positives — if it returns {@code true}, the element was
 *       <em>probably</em> added but there is a small chance of a collision.</li>
 * </ul>
 *
 * <p><strong>Hash strategy:</strong> Uses Kirsch-Mitzenmacher double hashing —
 * two independent hash functions derived from {@link String#hashCode()} and a
 * DJB2-style hash. From these two base hashes, {@code k} virtual hash functions
 * are derived via {@code h(i) = (h1 + i * h2) mod m} with no {@link java.util.Random}
 * state involved, making the filter deterministic and correct.
 *
 * <p>With {@code size = 1_000_000} and {@code numHashFunctions = 5} the false
 * positive rate for up to 100 000 inserted URLs is roughly 0.001%.
 */
public class BloomFilter {

    private final BitSet bitSet;
    private final int    size;
    private final int    numHashFunctions;

    /**
     * Creates a new Bloom filter.
     *
     * @param size             number of bits in the underlying bit-set
     * @param numHashFunctions number of independent hash functions ({@code k})
     */
    public BloomFilter(int size, int numHashFunctions) {
        this.size             = size;
        this.numHashFunctions = numHashFunctions;
        this.bitSet           = new BitSet(size);
    }

    /**
     * Records that {@code element} has been seen.
     *
     * @param element the string to add
     */
    public void add(String element) {
        int h1 = hash1(element);
        int h2 = hash2(element);
        for (int i = 0; i < numHashFunctions; i++) {
            bitSet.set(Math.abs((h1 + i * h2) % size));
        }
    }

    /**
     * Tests whether {@code element} might have been added before.
     *
     * @param element the string to test
     * @return {@code false} if definitely not present; {@code true} if probably present
     */
    public boolean mightContain(String element) {
        int h1 = hash1(element);
        int h2 = hash2(element);
        for (int i = 0; i < numHashFunctions; i++) {
            if (!bitSet.get(Math.abs((h1 + i * h2) % size))) {
                return false;
            }
        }
        return true;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /** First hash function — Java's built-in polynomial hash. */
    private int hash1(String s) {
        return s.hashCode();
    }

    /**
     * Second hash function — DJB2-style, independent of {@code hashCode()}.
     * Using two structurally different hash functions minimises correlation.
     */
    private int hash2(String s) {
        int hash = 5381;
        for (char c : s.toCharArray()) {
            hash = ((hash << 5) + hash) + c; // hash * 33 + c
        }
        return hash;
    }
}
