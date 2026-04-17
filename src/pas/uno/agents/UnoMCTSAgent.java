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


public class UnoMCTSAgent // UCTAgent
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
                copyGame.resolveMove(actualMove); //new agent object same move card index
                return new MCTSNode(copyGame.getView(copyGame.getPlayerOrder().getCurrentAgentIdx()),
                                    copyGame.getPlayerOrder().getCurrentLogicalPlayerIdx(),
                                    this);
            }
        }
    }

    public UnoMCTSAgent(final int playerIdx,
                    final long maxThinkingTimeInMS)
    {
        super(playerIdx, maxThinkingTimeInMS);
    }
    public int getActionCount(Node node)
    {
        if(node.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES)
        {
            return node.getOrderedLegalMoves().size();
        }
        else if (node.getNodeState() == Node.NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD)
        {
            return 2;
        }
        else
        {
            return 1;
        }
    }
    public int UCBSelect(Node node)
        {
            float bestValue = Float.NEGATIVE_INFINITY;
            int bestIdx = 0;
            double score = 0.0;
            long totalVisits =0;
            for(int i =0; i<this.getActionCount(node); i++)
            {
                 totalVisits = totalVisits + node.getQCount(i);
            }
            for(int i =0; i<this.getActionCount(node);i++)
            {
                if(node.getQCount(i)==0)
                {
                    return i;
                }
                else
                    {
                         score = node.getQValue(i) + Math.sqrt((2.0 * Math.log(totalVisits))/node.getQCount(i));
                    }
                    if(score>bestValue)
                    {
                        bestValue = (float) score;
                        bestIdx = i;
                    }
            }
            return bestIdx;
        }

    public boolean isFullyExpanded(Node node)
    {
        for(int i =0; i<this.getActionCount(node); i++)
        {
            if(node.getQCount(i) == 0) //no Q value means that we havent expanded that child from this node
            {
                return false;
            }
        }
        return true;
    }    

    public int pickUnvitedAction(Node node)
    {
        for(int i =0; i<this.getActionCount(node);i++)
        {
            if(node.getQCount(i)==0)
            {
                return i;
            }
        }
        return 0; //random
    }

    /**
     * A method to perform the MCTS search on the game tree
     *
     * @param   game            The {@link GameView} that should be the root of the game tree
     * @param   drawnCardIdx    This will be non-null when this method is being called by the 
     *                          <code>maybePlayDrawnCard</code> method of {@link Agent} and will
     *                          be <code>null</code> when being called by <code>chooseCardToPlay</code>
     *                          method of {@link Agent}
     * @return  The {@link Node} of the root who'se q-values should now be populated and ready to argmax
     */
    @Override
    public Node search(final GameView game,
                       final Integer drawnCardIdx)
    {
        // TODO: implement me!
        Node root = new MCTSNode(game,getLogicalPlayerIdx(), null);
        long startTime = System.currentTimeMillis();
        long timeLimit = this.getMaxThinkingTimeInMS() - 100L;
        
        Agent[] agents = new Agent[root.getGameView().getNumPlayers()];
        
                for (int p = 0; p < agents.length; p++) {
                    int pid = root.getGameView().getPlayerOrder().getAgentIdx(p);
                    agents[p] = new RandomAgent(pid, 1L);
                }
        while(System.currentTimeMillis() - startTime < timeLimit)
        {
            // if(root.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES)
            //     {
            List<Node> path = new ArrayList<>(); //keeping track of the path
            List<Integer> pathActionIdx = new ArrayList<>(); //keeping track of the action at that particular node in the path

            Node node = root;
            path.add(root);

            //pick a node
            while(!node.isTerminal() && this.isFullyExpanded(node))
            {
                int moveActionIdx = this.UCBSelect(node);
                Move childIteration = null;
                if(node.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES)
                {
                HandView myHand = node.getGameView().getHandView(node.getLogicalPlayerIdx());
                int handIdx = node.getOrderedLegalMoves().get(moveActionIdx);
                Card card = myHand.getCard(handIdx);
                
                    if(card.isWild())
                        {
                            childIteration = Move.createMove(this, handIdx, Color.getRandomColor(this.getRandom()));
                        }
                        else
                        {
                            childIteration = Move.createMove(this, handIdx);
                        }
                    }
                    else if (node.getNodeState() == Node.NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD) 
                    {
                        if(moveActionIdx == 1)
                        {
                            childIteration = null;
                        }
                        else 
                        {
                            HandView myHand = node.getGameView().getHandView(node.getLogicalPlayerIdx());
                int handIdx = myHand.size() -1;
                Card card = myHand.getCard(handIdx);
                
                    if(card.isWild())
                        {
                            childIteration = Move.createMove(this, handIdx, Color.getRandomColor(this.getRandom()));
                        }
                        else
                        {
                            childIteration = Move.createMove(this, handIdx);
                        }
                    }
                        }
                    else
                    {
                        Game tempGame = new Game (node.getGameView(), agents);
                        childIteration = tempGame.getMove();
                    }
                node = node.getChild(childIteration);
                path.add(node);
                pathActionIdx.add(moveActionIdx);
            }

            //pick an action
            if(!node.isTerminal() && node == root)
            {
                
            int actionIdx = this.pickUnvitedAction(node);
            Move childIteration =null;
            if(node.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES){
            HandView myHand = node.getGameView().getHandView(node.getLogicalPlayerIdx());
                int handIdx = node.getOrderedLegalMoves().get(actionIdx);
                Card card = myHand.getCard(handIdx);
                
                    if(card.isWild())
                        {
                            childIteration = Move.createMove(this, handIdx, Color.getRandomColor(this.getRandom()));
                        }
                        else
                        {
                            childIteration = Move.createMove(this, handIdx);
                        }
                    }
                    else if (node.getNodeState() == Node.NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD)
                    {
                        if(actionIdx == 1)
                        {
                            childIteration = null;
                        }
                        else
                            {
                                HandView myHand = node.getGameView().getHandView(node.getLogicalPlayerIdx());
                        int handIdx = myHand.size() -1; // the new card is added at the end of the hand
                        Card card = myHand.getCard(handIdx);
                
                    if(card.isWild())
                        {
                            childIteration = Move.createMove(this, handIdx, Color.getRandomColor(this.getRandom()));
                        }
                        else
                        {
                            childIteration = Move.createMove(this, handIdx);
                        }
                        }
                    }
                    else 
                    {
                        Game tempGame = new Game(node.getGameView(), agents);
                        childIteration = tempGame.getMove();
                    }
                node = node.getChild(childIteration);
                path.add(node);
                pathActionIdx.add(actionIdx);
                    }
                    float r = 0.0f;
            //rollout
           
                    Node child = node;
                    // int firstActionIdx =-1;
                    // Move firstAction = null;
                    // boolean isFirst = true;
                    // float r = 0.0f;
                                
                                Game simGame = new Game(child.getGameView(), agents);
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
                                        if(myCards ==0)
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
                                        int otherCards =0;
                                        for(int j =0; j<simGame.getNumPlayers();j++)
                                        {
                                            if(j!=getLogicalPlayerIdx())
                                            {
                                                otherCards = otherCards + simGame.getHand(j).size();
                                            }
                                        }
                                        r = otherCards - myCards; //can change this heuristic later
                                    }
                                    
            
                        // copyRoot.setQValueTotal(i, copyRoot.getQValueTotal(i) + r);
                        // copyRoot.setQCount(i, copyRoot.getQCount(i) + 1);
                        
                        // int playerIdx = copyRoot.getGameView().getPlayerOrder().getAgentIdx(i);
                        // agents[i] = new RandomAgent(playerIdx, 1L);
                    
                

                //backpropagation
                for(int i =0; i <pathActionIdx.size();i++)
                {
                    path.get(i).setQValueTotal(pathActionIdx.get(i), path.get(i).getQValueTotal(pathActionIdx.get(i)) + r);
                    path.get(i).setQCount(pathActionIdx.get(i), path.get(i).getQCount(pathActionIdx.get(i)) + 1);
                }
        }
    //}
        return root;
    }

    /**
     * A method to argmax the Q values inside a {@link Node}
     *
     * @param   node            The {@link Node} who has populated q-values
     * @return  The {@link Move} corresponding to whichever {@link Move} has the largest q-value. Note
     *          that this can be <code>null</code> if you choose to not play the drawn card (you will
     *          have to detect whether or not you are in that scenario by examining the @{link Node}'s state).
     */
    @Override
    public Move argmaxQValues(final Node node)
    {
        // TODO: implement me!
        {
            // TODO: implement me!
            Node.NodeState state = node.getNodeState();
            if(state == Node.NodeState.HAS_LEGAL_MOVES)
            {
                float value = Float.NEGATIVE_INFINITY;
                int valueIndex = 0;
                for(int i =0; i< node.getOrderedLegalMoves().size(); i++)
                {
                    if(node.getQValue(i)>value)
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
                    Move playMove = Move.createMove(this, moveIdx, Color.getRandomColor(this.getRandom()));
                    return playMove;
                }
                else
                {
                    Move playMove = Move.createMove(this, moveIdx);
                    return playMove;
                }
               
    
            }
            else if (state == Node.NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD)
            { //draw from stack and may or may not play the card
                float play = node.getQValue(0);
                float keep = node.getQValue(1);
                if(play>keep)
                {
                   HandView myHand = node.getGameView().getHandView(node.getLogicalPlayerIdx());
                   int drawCard = myHand.size() - 1;
                   Card card = myHand.getCard(drawCard);
                   if(card.isWild())
                   {
                    Move playMove = Move.createMove(this, drawCard, Color.getRandomColor(this.getRandom()));
                    return playMove;
                   }
                   else
                   {
                    Move playMove = Move.createMove(this, drawCard);
                    return playMove;
                   }
                }
                else
                {
                    return null;
                }
    
            }
            else //draw from stack since +2 or +4
            {
                Agent[] agents = new Agent[node.getGameView().getNumPlayers()];
            
                    for (int p = 0; p < agents.length; p++) {
                        int pid = node.getGameView().getPlayerOrder().getAgentIdx(p);
                        agents[p] = new RandomAgent(pid, 1L);
                    }
                Game copyGame = new Game(node.getGameView(), agents);
                return copyGame.getMove();
            }
        }
    }
}