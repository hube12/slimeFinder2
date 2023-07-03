package neil;


import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.rand.seed.WorldSeed;
import com.seedfinding.mccore.util.data.SpiralIterator;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.misc.SlimeChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static com.seedfinding.mcmath.util.Mth.max;
import static com.seedfinding.mcmath.util.Mth.min;

public class Main {

	public static class Requirements {
		public List<CPos> positiveSlime;
		public List<CPos> negativeSlime;
		public MCVersion version;

		public Requirements(List<CPos> positiveSlime, List<CPos> negativeSlime, MCVersion version) {
			this.positiveSlime = positiveSlime;
			this.negativeSlime = negativeSlime;
			this.version = version;
		}

		public boolean fastCheck(long seed, ChunkRand rand) {
			for (CPos slimeChunk : this.positiveSlime) {
				rand.setSlimeSeed(seed, slimeChunk.getX(), slimeChunk.getZ(), this.version);
				if (rand.next(31) % 2 != 0) {
					return false;
				}
			}
//			int d=0;
//			for (CPos slimeChunk : this.negativeSlime) {
//				rand.setSlimeSeed(seed, slimeChunk.getX(), slimeChunk.getZ(), this.version);
//				if (rand.next(31) % 2 == 0) {
//					/// Note that among 0 to 9 there is 0, 2, 4, 6, 8 that ends with 0 (in binary) so half
//					d+=1;
//				}
//			}
//			return d < this.negativeSlime.size() / 2;
			for (CPos slimeChunk : this.negativeSlime) {
				rand.setSlimeSeed(seed, slimeChunk.getX(), slimeChunk.getZ(), this.version);
				if (rand.next(31) % 10 == 0) {
					return false;
				}
			}
			return true;
		}

		public boolean check(long seed, ChunkRand rand) {
			for (CPos slimeChunk : this.positiveSlime) {
				rand.setSlimeSeed(seed, slimeChunk.getX(), slimeChunk.getZ(), this.version);
				if (rand.nextInt(10) != 0) return false;
			}
			for (CPos slimeChunk : this.negativeSlime) {
				rand.setSlimeSeed(seed, slimeChunk.getX(), slimeChunk.getZ(), this.version);
				if (rand.nextInt(10) == 0) return false;
			}
			return true;
		}
	}

	public static List<Long> findSlimeSingleThread(Requirements requirements) {
		ChunkRand rand = new ChunkRand();
		List<Long> lowerBits = new ArrayList<>();
		for (long lower = 0; lower < 1L << 18; lower++) {
			if (requirements.fastCheck(lower, rand)) {
				lowerBits.add(lower);
			}
		}
		List<Long> results = new ArrayList<>();
		for (long lower : lowerBits) {
			for (long upper = 0; upper < 1L << (48 - 18); upper++) {
				long seed = (upper << 18) | lower;
				if (requirements.fastCheck(seed, rand)) {
					results.add(seed);
				}
			}
		}
		return results;
	}

	public static LongStream findSlimeSeeds2(Requirements requirements, Requirements requirements2) {
		int bound = 17 + 6;
		LongStream lowerBitsStream = LongStream.range(0, 1L << bound)
				.filter(s -> requirements.fastCheck(s, new ChunkRand()))
				.parallel();

		LongStream seedStream = lowerBitsStream.flatMap(lowerBits ->
				LongStream.range(0, 1L << (48 - bound))
						.map(upperBits -> (upperBits << bound) | lowerBits)
		);
		return seedStream.filter(s -> requirements2.check(s, new ChunkRand()));
	}

	public static LongStream findSlimeSeeds(Requirements requirements) {
		int bound = 17 + 4;
		LongStream lowerBitsStream = LongStream.range(0, 1L << bound)
				.filter(s -> requirements.fastCheck(s, new ChunkRand()))
				.parallel();

		LongStream seedStream = lowerBitsStream.flatMap(lowerBits ->
				LongStream.range(0, 1L << (48 - bound))
						.map(upperBits -> (upperBits << bound) | lowerBits)
		);
		return seedStream.filter(s -> requirements.check(s, new ChunkRand()));
	}

	public static void findMe() {
		MCVersion version = MCVersion.v1_16;
		long seed = 43L;
		List<CPos> positive = generateSlimeChunks(seed, version, 30, true);
		List<CPos> negative = generateSlimeChunks(seed, version, 5, false);
		negative.removeIf(positive::contains);

		Requirements requirements = new Requirements(positive, negative, version);
		findSlimeSeeds(requirements).parallel().forEach(System.out::println);
	}


	public static void empty() {
		MCVersion version = MCVersion.v1_16;
		int size = 10;
		List<CPos> positive = new ArrayList<>();
		List<CPos> negative = generateSquare(new CPos(-size / 2, -size / 2).subtract(1,1), new CPos(size / 2, size / 2).subtract(1,1));
		negative.removeIf(positive::contains);

		Requirements requirements = new Requirements(positive, negative, version);

		size = 10;
		List<CPos> positive2 = new ArrayList<>();
		List<CPos> negative2 = generateSquare(new CPos(-size / 2, -size / 2 ).subtract(2,3), new CPos(size / 2 , size / 2 ).subtract(1,1));
		negative2.removeIf(positive2::contains);

		Requirements requirements2 = new Requirements(positive2, negative2, version);
		findSlimeSeeds2(requirements, requirements2).parallel().forEach(s ->

		{
			int counter = 0;
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					int radius = slimeLessDistanceTo(s, new CPos(x, z), version);
					if (radius > 6) {
						counter += 1;
					}
				}
			}
			System.out.printf("%d\n", s, counter);
			if (counter >= 5) {
				System.out.printf("%d\n", s, counter);
			}
		});


	}


	public static int slimeLessDistanceTo(long seed, CPos pos, MCVersion version) {
		ChunkRand rand = new ChunkRand();
		for (int radius = 0; radius < 20; radius++) {
			for (int x = -radius + pos.getX(); x <= radius + pos.getX(); x++) {
				for (int z = -radius + pos.getZ(); z < radius + pos.getZ(); z++) {
					rand.setSlimeSeed(seed, x, z, version);
					if (rand.nextInt(10) == 0) return radius;
				}
			}
		}
		return Integer.MAX_VALUE;
	}

	public static void main(String[] args) {
//		findMe();
		empty();
	}

	private static List<CPos> generateSquare(CPos corner1, CPos corner2) {
		List<CPos> slimeChunks = new ArrayList<>();
		ChunkRand rand = new ChunkRand();

		for (int x = min(corner1.getX(), corner2.getX()); x <= max(corner1.getX(), corner2.getX()); x++) {
			for (int z = min(corner1.getZ(), corner2.getZ()); z <= max(corner1.getZ(), corner2.getZ()); z++) {
				slimeChunks.add(new CPos(x, z));
			}
		}
		return slimeChunks;
	}

	private static List<CPos> generateSlimeChunks(Long worldSeed, MCVersion version, int count, boolean positive) {
		List<CPos> slimeChunks = new ArrayList<>();
		ChunkRand rand = new ChunkRand();

		while (slimeChunks.size() < count) {
			CPos pos = new CPos(rand.nextInt(100001) - 5000, rand.nextInt(100001) - 5000);
			rand.setSlimeSeed(worldSeed, pos.getX(), pos.getZ(), version);
			if (rand.nextInt(10) == 0) {
				if (positive) slimeChunks.add(pos);
			} else {
				if (!positive) slimeChunks.add(pos);
			}
		}

		System.out.println("Chunks are " + slimeChunks);
		return slimeChunks;
	}
}