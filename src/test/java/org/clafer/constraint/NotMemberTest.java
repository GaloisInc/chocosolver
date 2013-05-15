package org.clafer.constraint;

import org.clafer.Util;
import static org.junit.Assert.*;
import org.junit.Test;
import solver.Solver;
import solver.variables.IntVar;
import solver.variables.SetVar;
import solver.variables.VariableFactory;

/**
 *
 * @author jimmy
 */
public class NotMemberTest extends ConstraintTest {

    private void checkCorrectness(IntVar element, SetVar set) {
        assertFalse(Util.in(element.getValue(), set.getValue()));
    }

    @Test(timeout = 60000)
    public void testNotMember() {
        for (int repeat = 0; repeat < 10; repeat++) {
            Solver solver = new Solver();
            int num = nextInt(10);

            IntVar element = VariableFactory.enumerated("element", -nextInt(10), nextInt(10), solver);
            SetVar set = VariableFactory.set("set", Util.range(-nextInt(10), nextInt(10)), solver);

            solver.post(Constraints.notMember(element, set));

            assertTrue(randomizeStrategy(solver).findSolution());
            checkCorrectness(element, set);
            for (int solutions = 1; solutions < 10 && solver.nextSolution(); solutions++) {
                checkCorrectness(element, set);
            }
        }
    }

    @Test(timeout = 60000)
    public void quickTest() {
        Solver solver = new Solver();

        IntVar element = VariableFactory.enumerated("element", -1, 3, solver);
        SetVar set = VariableFactory.set("set", new int[]{0, 1, 2, 3, 4, 5}, solver);

        solver.post(Constraints.notMember(element, set));

        assertEquals(192, randomizeStrategy(solver).findAllSolutions());
    }
}
