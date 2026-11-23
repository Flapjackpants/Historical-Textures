package dev.historicaltextures.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseTimelineTest {
	@Test
	void orderingChainIsMonotonic() {
		List<String> chain = List.of(
				"rd-132328",
				"0.0.14a",
				"20100130",
				"20100618",
				"v1.0.8",
				"Alpha v1.2.6",
				"Beta 1.7.3",
				"Beta 1.9 Prerelease 3",
				"1.0.0",
				"12w05a",
				"1.2.5",
				"12w32a",
				"1.4.7",
				"13w01a",
				"1.6.4",
				"14w34a",
				"1.8",
				"1.8.9",
				"15w31a",
				"1.12.2",
				"18w43a",
				"1.14",
				"26.1"
		);
		for (int i = 1; i < chain.size(); i++) {
			assertTrue(
					ReleaseTimeline.releaseKey(chain.get(i - 1)) < ReleaseTimeline.releaseKey(chain.get(i)),
					chain.get(i - 1) + " (" + ReleaseTimeline.releaseKey(chain.get(i - 1)) + ") !< "
							+ chain.get(i) + " (" + ReleaseTimeline.releaseKey(chain.get(i)) + ")"
			);
		}
	}

	@Test
	void snapshotsMapToTheReleaseTheyLedTo() {
		assertTrue(ReleaseTimeline.releaseKey("12w05a") < ReleaseTimeline.releaseKey("1.2"));
		assertTrue(ReleaseTimeline.releaseKey("15w31a") > ReleaseTimeline.releaseKey("1.8.9"));
		assertTrue(ReleaseTimeline.releaseKey("15w31a") < ReleaseTimeline.releaseKey("1.9"));
	}

	@Test
	void releasePreReleasesSortBeforeTheirRelease() {
		assertTrue(ReleaseTimeline.releaseKey("1.14 Pre-Release 1") < ReleaseTimeline.releaseKey("1.14"));
		assertTrue(ReleaseTimeline.releaseKey("1.14 Pre-Release 1") > ReleaseTimeline.releaseKey("1.13.2"));
		assertTrue(ReleaseTimeline.releaseKey("1.13-pre5") < ReleaseTimeline.releaseKey("1.13"));
		assertTrue(ReleaseTimeline.releaseKey("1.20.1 rc1") < ReleaseTimeline.releaseKey("1.20.1"));
	}

	@Test
	void unknownVersionsReturnMinusOne() {
		for (String unknown : List.of("snap3", "pre5", "release", "", "25w14craftmine", "Pre-release", "preview", "Test Build 3", "rc1", "1.9+")) {
			assertEquals(-1, ReleaseTimeline.releaseKey(unknown), unknown);
		}
		assertEquals(-1, ReleaseTimeline.releaseKey(null));
		assertEquals(-1, ReleaseTimeline.releaseKey("   "));
	}

	@Test
	void eraEncodings() {
		assertEquals(800_000 + 1 * 10_000 + 0 * 100 + 8, ReleaseTimeline.releaseKey("v1.0.8"));
		assertEquals(900_000 + 1 * 10_000 + 7 * 100 + 3, ReleaseTimeline.releaseKey("Beta 1.7.3"));
		assertEquals(900_000 + 1 * 10_000 + 2 * 100 + 1, ReleaseTimeline.releaseKey("1.2_01"));
		assertEquals(1_002_005, ReleaseTimeline.releaseKey("1.2.5"));
		assertEquals(26_001_000, ReleaseTimeline.releaseKey("26.1"));
	}
}
