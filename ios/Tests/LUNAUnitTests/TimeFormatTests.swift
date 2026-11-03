import XCTest
@testable import LUNA

final class TimeFormatTests: XCTestCase {
    func testFormatsMinutesAndPaddedSeconds() {
        XCTAssertEqual(TimeFormat.clock(204), "3:24")
        XCTAssertEqual(TimeFormat.clock(9), "0:09")
        XCTAssertEqual(TimeFormat.clock(3600), "60:00")
    }

    func testInvalidValuesShowZero() {
        XCTAssertEqual(TimeFormat.clock(-5), "0:00")
        XCTAssertEqual(TimeFormat.clock(.nan), "0:00")
        XCTAssertEqual(TimeFormat.clock(.infinity), "0:00")
    }
}
