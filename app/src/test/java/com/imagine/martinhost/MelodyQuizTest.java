package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;

public class MelodyQuizTest {
 @Test public void partySetAlwaysHasSixTracksPerDifficulty(){List<MelodyQuiz.Track> r=MelodyQuiz.partyRounds(new Random(7));assertEquals(18,r.size());for(int i=0;i<6;i++)assertEquals(1,r.get(i).tier);for(int i=6;i<12;i++)assertEquals(2,r.get(i).tier);for(int i=12;i<18;i++)assertEquals(3,r.get(i).tier);Set<String> q=new HashSet<>();for(MelodyQuiz.Track t:r)q.add(t.query());assertEquals(18,q.size());}
 @Test public void poolIsLargerThanOnePartySoReplaysVary(){assertTrue(MelodyQuiz.all().size()>=30);}
}
