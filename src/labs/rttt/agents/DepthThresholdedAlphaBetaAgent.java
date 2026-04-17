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
import src.labs.rttt.ordering.MoveOrderer;


public class DepthThresholdedAlphaBetaAgent
    extends SearchAgent
{

    public static final int DEFAULT_MAX_DEPTH = 3;

    private int maxDepth;

    public DepthThresholdedAlphaBetaAgent(PlayerType myPlayerType)
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

    public Node alphaBeta(Node node,
                          double alpha,
                          double beta)
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
        List<Node> children = MoveOrderer.orderChildren(node.getChildren());

        for(Node child : children)
        {
            Node result = this.alphaBeta(child, alpha, beta);
            double utility = result.getUtilityValue();
            child.setUtilityValue(utility);

            if(isMaxNode)
            {
                if(utility > bestUtility)
                {
                    bestUtility = utility;
                    bestNode = child;
                }

                if(bestUtility > beta)
                {
                    return bestNode;
                }

                alpha = Math.max(alpha, bestUtility);
            } else
            {
                if(utility < bestUtility)
                {
                    bestUtility = utility;
                    bestNode = child;
                }

                if(bestUtility < alpha)
                {
                    return bestNode;
                }

                beta = Math.min(beta, bestUtility);
            }
        }

        return bestNode;
    }

    public Node search(Node node)
    {
        return this.alphaBeta(node, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    @Override
    public void afterGameEnds(final RecursiveTicTacToeGameView game) {}
}
