package org.example.nabat.incident.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FixCheckTallyTest {

    @ParameterizedTest(name = "fixed={0} partly={1} notFixed={2} reporter={3} -> {4}")
    @CsvSource(nullValues = "-", value = {
        "0, 0, 0, -,         UNCHECKED",
        "1, 0, 0, -,         CONFIRMED",
        "3, 0, 0, FIXED,     CONFIRMED",
        // The reporter's "no" is enough on its own, however many say yes.
        "5, 0, 1, NOT_FIXED, DISPUTED",
        "1, 0, 2, -,         DISPUTED",
        "0, 0, 1, -,         DISPUTED",
        // A reporter's "yes" does not outvote the others' "no".
        "1, 0, 2, FIXED,     DISPUTED",
        "2, 0, 1, -,         MIXED",
        "1, 1, 0, -,         MIXED",
        "0, 1, 0, PARTLY,    MIXED",
        "1, 1, 1, -,         MIXED",
    })
    void addsUp(int fixed, int partly, int notFixed, FixVerdict reporter, FixOutcome expected) {
        assertThat(new FixCheckTally(fixed, partly, notFixed, reporter).outcome()).isEqualTo(expected);
    }
}
