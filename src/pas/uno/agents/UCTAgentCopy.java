package pas.uno.agents;


// SYSTEM IMPORTS
import edu.bu.pas.uno.Card;
import edu.bu.pas.uno.Game.GameView;
import edu.bu.pas.uno.Hand.HandView;
import edu.bu.pas.uno.agents.MCTSAgent;
import edu.bu.pas.uno.enums.Color;
import edu.bu.pas.uno.enums.Value;
import edu.bu.pas.uno.moves.Move;
import edu.bu.pas.uno.tree.Node;
import edu.bu.pas.uno.Game;
import edu.bu.pas.uno.agents.Agent;
import edu.bu.pas.uno.agents.RandomAgent;


import java.util.Random;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;


// JAVA PROJECT IMPORTS


public class UCTAgentCopy
    extends MCTSAgent
{

    public static class MCTSNode
        extends Node
    {
        public MCTSNode(final GameView game,
                        final int logicalPlayerIdx,
                        final Node parent)
        {
            super(game, logicalPlayerIdx, parent);
        }

        @Override
        public Node getChild(final Move move)
        {
            Agent[] agents = new Agent[this.getGameView().getNumPlayers()];
            for(int i =0; i<agents.length; i++)
            {
                int playerIdx = this.getGameView().getPlayerOrder().getAgentIdx(i);
                agents[i] = new RandomAgent(playerIdx, 1L);
            }
            Game copyGame = new Game(this.getGameView(), agents);

            Move actualMove = move;
            if(move !=null)
            {
                Agent currentAgent = copyGame.getCurrentAgent();
                if(move.getNewColorIfWild() !=null)
                {
                    actualMove = Move.createMove(currentAgent, move.getCardToPlayIdx(), move.getNewColorIfWild());
                }
                else
                {
                    actualMove = Move.createMove(currentAgent, move.getCardToPlayIdx());
                }
            }
            copyGame.resolveMove(actualMove);
            return new MCTSNode(copyGame.getView(copyGame.getPlayerOrder().getCurrentAgentIdx()),
                                copyGame.getPlayerOrder().getCurrentLogicalPlayerIdx(),
                                this);
        }
    }

    public UCTAgentCopy(final int playerIdx,
                    final long maxThinkingTimeInMS)
    {
        super(playerIdx, maxThinkingTimeInMS);
    }

    private int getActionCount(Node node)
    {
        if(node.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES)
        {
            return node.getOrderedLegalMoves().size();
        }
        else if(node.getNodeState() == Node.NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD)
        {
            return 2;
        }
        else
        {
            return 1;
        }
    }

    private Move createMoveFromActionIdx(Node node, int actionIdx, Agent[] agents)
    {
        if(node.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES)
        {
            HandView myHand = node.getGameView().getHandView(node.getLogicalPlayerIdx());
            int handIdx = node.getOrderedLegalMoves().get(actionIdx);
            Card card = myHand.getCard(handIdx);
            if(card.isWild())
            {
                return Move.createMove(this, handIdx, Color.getRandomColor(this.getRandom()));
            }
            else
            {
                return Move.createMove(this, handIdx);
            }
        }
        else if(node.getNodeState() == Node.NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD)
        {
            if(actionIdx == Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX)
            {
                return null;
            }
            else
            {
                HandView myHand = node.getGameView().getHandView(node.getLogicalPlayerIdx());
                int handIdx = myHand.size() - 1;
                Card card = myHand.getCard(handIdx);
                if(card.isWild())
                {
                    return Move.createMove(this, handIdx, Color.getRandomColor(this.getRandom()));
                }
                else
                {
                    return Move.createMove(this, handIdx);
                }
            }
        }
        else
        {
            Game tempGame = new Game(node.getGameView(), agents);
            return tempGame.getMove();
        }
    }

    private int UCBSelect(Node node)
    {
        double bestValue = Double.NEGATIVE_INFINITY;
        int bestIdx = 0;
        int actionCount = getActionCount(node);
        long totalVisits = 0;
        for(int i = 0; i < actionCount; i++)
        {
            totalVisits += node.getQCount(i);
        }
        for(int i = 0; i < actionCount; i++)
        {
            double score;
            if(node.getQCount(i) == 0)
            {
                score = Double.POSITIVE_INFINITY;
            }
            else
            {
                score = node.getQValue(i) + Math.sqrt((2.0 * Math.log(totalVisits)) / node.getQCount(i));
            }
            if(score > bestValue)
            {
                bestValue = score;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private boolean isFullyExpanded(Node node)
    {
        for(int i = 0; i < getActionCount(node); i++)
        {
            if(node.getQCount(i) == 0)
            {
                return false;
            }
        }
        return true;
    }

    private int pickUnvisitedAction(Node node)
    {
        for(int i = 0; i < getActionCount(node); i++)
        {
            if(node.getQCount(i) == 0)
            {
                return i;
            }
        }
        return 0;
    }

    @Override
    public Node search(final GameView game,
                       final Integer drawnCardIdx)
    {
        Node root = new MCTSNode(game, getLogicalPlayerIdx(), null);
        long startTime = System.currentTimeMillis();
        long timeLimit = Math.max(1L, (this.getMaxThinkingTimeInMS() - 100L) / 10L);
        System.err.println("DEBUG search: state=" + root.getNodeState()
            + " timeLimit=" + timeLimit + "ms stateCount=" + root.getStateCount()
            + " isTerminal=" + root.isTerminal()
            + " logicalPlayer=" + getLogicalPlayerIdx()
            + " legalMoves=" + (root.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES ? root.getOrderedLegalMoves().size() : "N/A"));

        Agent[] agents = new Agent[root.getGameView().getNumPlayers()];
        for(int p = 0; p < agents.length; p++)
        {
            int pid = root.getGameView().getPlayerOrder().getAgentIdx(p);
            agents[p] = new RandomAgent(pid, 1L);
        }

        while(System.currentTimeMillis() - startTime < timeLimit)
        {
            List<Node> path = new ArrayList<>();
            List<Integer> pathActionIdx = new ArrayList<>();

            Node node = root;
            path.add(root);

            // Step 1: Selection
            while(!node.isTerminal() && isFullyExpanded(node))
            {
                int moveActionIdx = UCBSelect(node);
                Move childIteration = createMoveFromActionIdx(node, moveActionIdx, agents);
                pathActionIdx.add(moveActionIdx);
                node = node.getChild(childIteration);
                path.add(node);
            }

            // Step 2: Expansion
            if(!node.isTerminal())
            {
                int actionIdx = pickUnvisitedAction(node);
                Move childIteration = createMoveFromActionIdx(node, actionIdx, agents);
                pathActionIdx.add(actionIdx);
                node = node.getChild(childIteration);
                path.add(node);
            }

            // Step 3: Rollout
            float r = 0.0f;
            Game simGame = new Game(node.getGameView(), agents);
            int rolloutCap = 20;
            int moveCount = 0;
            while(!simGame.isOver() && moveCount < rolloutCap)
            {
                Move randomMove = simGame.getMove();
                simGame.resolveMove(randomMove);
                moveCount++;
            }
            int myCards = simGame.getHand(getLogicalPlayerIdx()).size();
            if(simGame.isOver())
            {
                if(myCards == 0)
                {
                    r = 1.0f;
                }
                else
                {
                    r = -1.0f;
                }
            }
            else
            {
                int otherCards = 0;
                for(int j = 0; j < simGame.getNumPlayers(); j++)
                {
                    if(j != getLogicalPlayerIdx())
                    {
                        otherCards += simGame.getHand(j).size();
                    }
                }
                r = otherCards - myCards;
            }

            // Step 4: Backpropagation
            for(int i = 0; i < pathActionIdx.size(); i++)
            {
                Node n = path.get(i);
                int aIdx = pathActionIdx.get(i);
                n.setQValueTotal(aIdx, n.getQValueTotal(aIdx) + r);
                n.setQCount(aIdx, n.getQCount(aIdx) + 1);
            }
        }
        long elapsed = System.currentTimeMillis() - startTime;
        System.err.println("DEBUG search done: elapsed=" + elapsed + "ms");
        for(int q = 0; q < getActionCount(root); q++)
        {
            System.err.println("  Q[" + q + "] val=" + root.getQValue(q) + " count=" + root.getQCount(q));
        }
        return root;
    }

    @Override
    public Move argmaxQValues(final Node node)
    {
        Node.NodeState state = node.getNodeState();
        if(state == Node.NodeState.HAS_LEGAL_MOVES)
        {
            float value = Float.NEGATIVE_INFINITY;
            int valueIndex = 0;
            for(int i = 0; i < node.getOrderedLegalMoves().size(); i++)
            {
                if(node.getQValue(i) > value)
                {
                    value = node.getQValue(i);
                    valueIndex = i;
                }
            }
            int moveIdx = node.getOrderedLegalMoves().get(valueIndex);
            HandView myHand = node.getGameView().getHandView(node.getLogicalPlayerIdx());
            Card card = myHand.getCard(moveIdx);
            if(card.isWild())
            {
                return Move.createMove(this, moveIdx, Color.getRandomColor(this.getRandom()));
            }
            else
            {
                return Move.createMove(this, moveIdx);
            }
        }
        else if(state == Node.NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD)
        {
            float play = node.getQValue(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX);
            float keep = node.getQValue(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX);
            if(play > keep)
            {
                HandView myHand = node.getGameView().getHandView(node.getLogicalPlayerIdx());
                int drawCard = myHand.size() - 1;
                Card card = myHand.getCard(drawCard);
                if(card.isWild())
                {
                    return Move.createMove(this, drawCard, Color.getRandomColor(this.getRandom()));
                }
                else
                {
                    return Move.createMove(this, drawCard);
                }
            }
            else
            {
                return null;
            }
        }
        else
        {
            Agent[] agents = new Agent[node.getGameView().getNumPlayers()];
            for(int p = 0; p < agents.length; p++)
            {
                int pid = node.getGameView().getPlayerOrder().getAgentIdx(p);
                agents[p] = new RandomAgent(pid, 1L);
            }
            Game copyGame = new Game(node.getGameView(), agents);
            return copyGame.getMove();
        }
    }
}
