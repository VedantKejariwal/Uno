import edu.bu.pas.uno.Card;
import edu.bu.pas.uno.Game;
import edu.bu.pas.uno.agents.Agent;
import edu.bu.pas.uno.agents.RandomAgent;
import edu.bu.pas.uno.enums.Color;
import edu.bu.pas.uno.enums.Observability;
import edu.bu.pas.uno.enums.Value;
import edu.bu.pas.uno.moves.Move;
import java.util.Random;

/** Headless probe: after +2 on discard, next player's getMove() should grow their hand. */
public class DrawPenaltyProbe {
    public static void main(String[] args) {
        Agent[] agents = new Agent[]{new RandomAgent(0, 1L), new RandomAgent(1, 2L)};
        Game g = new Game(Observability.FULL, new Random(42), agents);
        // P0 plays a red +2 onto red zero; P1 cannot stack (no DRAW_TWO in hand)
        g.getHand(0).getCards().clear();
        g.getHand(0).add(new Card(Color.RED, Value.DRAW_TWO));
        g.getHand(1).getCards().clear();
        g.getHand(1).add(new Card(Color.BLUE, Value.ONE));
        g.setCurrentColor(Color.RED);
        g.setLastPlayedCard(new Card(Color.RED, Value.ZERO));
        int p1Before = g.getHand(1).size();
        int unresolvedBefore = g.getUnresolvedCards().total();
        Move m = Move.createMove(agents[0], 0);
        g.resolveMove(m);
        int unresolvedMid = g.getUnresolvedCards().total();
        System.out.println("P1 hand before victim turn: " + p1Before);
        System.out.println("Unresolved total after +2 played: " + unresolvedMid + " (was " + unresolvedBefore + ")");
        Move victim = g.getMove();
        int p1After = g.getHand(1).size();
        System.out.println("getMove after penalty returned: " + victim);
        System.out.println("P1 hand size after getMove: " + p1After + " (delta " + (p1After - p1Before) + ")");
        System.out.println("Unresolved after getMove: " + g.getUnresolvedCards().total());
    }
}
