package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class PartyGamesTest {
 @Test public void answersUseWordsNotSubstrings(){assertTrue(PartyGames.matches("это яма","яма|яму"));assertFalse(PartyGames.matches("упрямая","яма"));assertTrue(PartyGames.matches("ёлка","елка"));assertFalse(PartyGames.matches("что угодно",""));}
 @Test public void offlineRoundsHaveAnswersAndUniqueIds(){Set<String> ids=new HashSet<>();for(PartyGames.Game g:PartyGames.all()){assertTrue(ids.add(g.id));assertFalse(g.rules.isBlank());assertFalse(g.example.isBlank());if(!g.id.equals("melody")&&!g.id.equals("time_machine")){assertTrue(g.rounds.size()>=3);for(PartyGames.Round r:g.rounds){assertFalse(r.question.isBlank());if(!r.judged)assertFalse(r.answer.isBlank());}}}assertEquals(12,ids.size());}
}
