package re.heartdroid.cmd;

import heart.uncertainty.ConflictSet;
import heart.uncertainty.ConflictSetResolution;
import heart.uncertainty.UncertainTrue;
import heart.xtt.Rule;

import java.util.AbstractMap.SimpleEntry;
import java.util.LinkedList;

public class ConflictSetMostCertainWin implements ConflictSetResolution {

    @Override
    public LinkedList<SimpleEntry<Rule, UncertainTrue>> resolveConflictSet(
            ConflictSet cs) {
        LinkedList<SimpleEntry<Rule, UncertainTrue>> result = new LinkedList<SimpleEntry<Rule, UncertainTrue>>();

        SimpleEntry<Rule, UncertainTrue> winner = cs.getFirst();

        for(SimpleEntry<Rule, UncertainTrue> se : cs){
            if(se.getValue().getCertinatyFactor() > winner.getValue().getCertinatyFactor()) {
                winner = se;
            }
        }
        result.add(winner);
        return result;
    }

}
