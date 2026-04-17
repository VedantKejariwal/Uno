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


public class ExpectedOutcomeAgent
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
            copyGame.resolveMove(actualMove); //new agent object same move card index
            return new MCTSNode(copyGame.getView(copyGame.getPlayerOrder().getCurrentAgentIdx()),
                                copyGame.getPlayerOrder().getCurrentLogicalPlayerIdx(),
                                this);
        }
    }

    public ExpectedOutcomeAgent(final int playerIdx,
                                final long maxThinkingTimeInMS)
    {
        super(playerIdx, maxThinkingTimeInMS);
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
        Node root = new MCTSNode(game, getLogicalPlayerIdx(), null);
        Move playMove = null;
        long startTime = System.currentTimeMillis();
        long timeLimit = Math.max(1L, this.getMaxThinkingTimeInMS()-100L);
        if(root.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES)
            {
            
                List<Integer> legalMoves = root.getOrderedLegalMoves();
                Node[] children = new Node[legalMoves.size()];
                for(int i =0; i<legalMoves.size(); i++)
                {
                    HandView myHand = root.getGameView().getHandView(root.getLogicalPlayerIdx());
                    int handIdx = legalMoves.get(i);
                    Card card = myHand.getCard(handIdx);
    
                    Move childIteration;
                    if(card.isWild())
                        {
                            childIteration = Move.createMove(this, handIdx, Color.getRandomColor(this.getRandom()));
                        }
                        else
                        {
                            childIteration = Move.createMove(this, handIdx);
                        }
                    children[i]=root.getChild(childIteration);
                }
                Agent[] agents = new Agent[root.getGameView().getNumPlayers()];
        
                for (int p = 0; p < agents.length; p++) {
                    int pid = root.getGameView().getPlayerOrder().getAgentIdx(p);
                    agents[p] = new RandomAgent(pid, 1L);
                }
        while (System.currentTimeMillis() - startTime < timeLimit)
        {
            Node copyRoot = root;
            int firstActionIdx =-1;
            Move firstAction = null;
        boolean isFirst = true;
        
        for(int i =0; i<legalMoves.size(); i++)
            {
                        Node child = children[i];
                        Game simGame = new Game(child.getGameView(), agents);
                        int rolloutCap = 20;
                        int moveCount =0;
                        while(!simGame.isOver() && moveCount < rolloutCap)
                            {
                                Move randomMove = simGame.getMove();
                                simGame.resolveMove(randomMove);
                                moveCount++;
                            } 
                            float r = 0.0f;
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
                            
    
                copyRoot.setQValueTotal(i, copyRoot.getQValueTotal(i) + r);
                copyRoot.setQCount(i, copyRoot.getQCount(i) + 1);
                
                // int playerIdx = copyRoot.getGameView().getPlayerOrder().getAgentIdx(i);
                // agents[i] = new RandomAgent(playerIdx, 1L);
            }
            
        
            // int randomIdx = this.getRandom().nextInt(copyRoot.getOrderedLegalMoves().size());
            // HandView myHand = copyRoot.getGameView().getHandView(copyRoot.getLogicalPlayerIdx());
            // int handIdx = copyRoot.getOrderedLegalMoves().get(randomIdx);
            // Card card = myHand.getCard(handIdx);
            // if(card.isWild())
            // {
            //     playMove = Move.createMove(this, handIdx, Color.getRandomColor(this.getRandom()));
            // }
            // else
            // {
            //     playMove = Move.createMove(this, handIdx);
            // }
            // if(isFirst)
            // {
            //     firstActionIdx = randomIdx;
            //     firstAction = playMove;
            //     isFirst = false;
            // }
            //copyRoot = copyRoot.getChild(playMove);
        
        
        // Agent[] agents = new Agent[copyRoot.getGameView().getNumPlayers()];
        // for(int i =0; i<agents.length; i++)
        //     {
        //         int playerIdx = copyRoot.getGameView().getPlayerOrder().getAgentIdx(i);
        //         agents[i] = new RandomAgent(playerIdx, 1L);
        //     }
        //     Game simGame = new Game(copyRoot.getGameView(), agents);
        //     int rolloutCap = 60;
        // int moveCount =0;

            
        }    

    }
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
            float play = node.getQValue(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX);
            float keep = node.getQValue(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX);
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
