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


public class DepthThresholdedMinimaxAgent
    extends SearchAgent
{

    public static final int DEFAULT_MAX_DEPTH = 3;

    private int maxDepth;

    public DepthThresholdedMinimaxAgent(PlayerType myPlayerType)
    {
        super(myPlayerType);
        this.maxDepth = DEFAULT_MAX_DEPTH;
    }

    public final int getMaxDepth() { return this.maxDepth; }
    public void setMaxDepth(int i) { this.maxDepth = i; }

    public String getTabs(Node node)
    {
        StringBuilder b = new StringBuilder();
        for(int idx = 0; idx < node.getDepth(); ++idx)
        {
            b.append("\t");
        }
        return b.toString();
    }

    public Node minimax(Node node)
    {
        // uncomment if you want to see the tree being made
        // System.out.println(this.getTabs(node) + "Node(currentPlayer=" + node.getCurrentPlayerType() +
        //      " isTerminal=" + node.isTerminal() + " lastMove=" + node.getLastMove() + ")");

        /**
         * TODO: complete me!
         */
        if(node.isTerminal())
        {
            return node;
        }

        if(node.getDepth() >= this.getMaxDepth())
        {
            node.setUtilityValue(Heuristics.calculateHeuristicValue(node));
            return node;
        }

        boolean isMaxNode = node.getCurrentPlayerType() == node.getMyPlayerType();
        Node bestNode = null;
        double bestUtility = isMaxNode ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

        for(Node child : node.getChildren())
        {
            Node result = this.minimax(child);
            double utility = result.getUtilityValue();
            child.setUtilityValue(utility);

            if(isMaxNode)
            {
                if(utility > bestUtility)
                {
                    bestUtility = utility;
                    bestNode = child;
                }
            } else
            {
                if(utility < bestUtility)
                {
                    bestUtility = utility;
                    bestNode = child;
                }
            }
        }

        return bestNode;
    }

    public Node search(Node node)
    {
        return this.minimax(node);
    }

    @Override
    public void afterGameEnds(final RecursiveTicTacToeGameView game) {}
}
