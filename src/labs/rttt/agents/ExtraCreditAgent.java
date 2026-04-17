package src.labs.rttt.agents;


// SYSTEM IMPORTS
import edu.bu.labs.rttt.agents.SearchAgent;
import edu.bu.labs.rttt.game.CellType;
import edu.bu.labs.rttt.game.PlayerType;
import edu.bu.labs.rttt.game.RecursiveTicTacToeGame;
import edu.bu.labs.rttt.game.RecursiveTicTacToeGame.RecursiveTicTacToeGameView;
import edu.bu.labs.rttt.traversal.Node;
import edu.bu.labs.rttt.utils.Coordinate;
import edu.bu.labs.rttt.utils.Pair;

import java.util.List;
import java.util.Map;


// JAVA PROJECT IMPORTS
import src.labs.rttt.heuristics.Heuristics;


public class ExtraCreditAgent
    extends SearchAgent
{

    public ExtraCreditAgent(PlayerType myPlayerType)
    {
        super(myPlayerType);
    }

    public Node search(Node node)
    {
        // whatever flavor of adversarial search you want!
        /**
         * TODO: complete me!
         */
        return null;
    }

    @Override
    public void afterGameEnds(final RecursiveTicTacToeGameView game) {}
}
