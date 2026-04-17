import edu.bu.pas.uno.Game;
import edu.bu.pas.uno.agents.RandomAgent;
import edu.bu.pas.uno.agents.Agent;
import edu.bu.pas.uno.enums.Observability;
import edu.bu.pas.uno.Card;
import edu.bu.pas.uno.moves.Move;
import edu.bu.pas.uno.enums.Color;
import edu.bu.pas.uno.enums.Value;
import java.util.Random;

public class TestGame {
    public static void main(String[] args) {
        Agent[] agents = new Agent[]{new RandomAgent(0, 1L), new RandomAgent(1, 1L)};
        Game original = new Game(Observability.FULL, new Random(), agents);
        
        // Force the first player to play a DRAW_TWO
        original.getHand(0).clear();
        original.getHand(0).addCard(new Card(Color.RED, Value.DRAW_TWO));
        original.setCurrentColor(Color.RED);
        original.setLastPlayedCard(new Card(Color.RED, Value.ZERO));
        
        Move m = Move.createMove(agents[0], 0);
        original.resolveMove(m); // This should put 2 cards in UnresolvedCardBuffer
        
        System.out.println("Unresolved cards: " + original.getUnresolvedCards().size());
        
        Game.GameView view = original.getView(1); // Player 1's view
        try {
            System.out.println("Calling new Game(view, agents) with Unresolved Cards...");
            Game copy1 = new Game(view, agents);
            System.out.println("SUCCESS");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}