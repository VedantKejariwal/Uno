package pas.uno.agents;


// SYSTEM IMPORTS
import edu.bu.pas.uno.Card;
import edu.bu.pas.uno.Game;
import edu.bu.pas.uno.Game.GameView;
import edu.bu.pas.uno.Hand.HandView;
import edu.bu.pas.uno.agents.Agent;
import edu.bu.pas.uno.agents.MCTSAgent;
import edu.bu.pas.uno.agents.RandomAgent;
import edu.bu.pas.uno.enums.Color;
import edu.bu.pas.uno.moves.Move;
import edu.bu.pas.uno.tree.Node;

// JAVA PROJECT IMPORTS


public class ExpectedOutcomeAgentCopy
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
            // Important fix:
            // new Game(GameView) leaves the agent array null, which caused the NPEs seen in the logs.
            Agent[] agents = new Agent[this.getGameView().getNumPlayers()];
            for(int logicalIdx = 0; logicalIdx < agents.length; logicalIdx++)
            {
                int playerIdx = this.getGameView().getPlayerOrder().getAgentIdx(logicalIdx);
                agents[logicalIdx] = new RandomAgent(playerIdx, 1L);
            }

            Game copyGame = new Game(this.getGameView(), agents);

            // Rebuild the move using the copied game's current agent.
            // The framework checks move.getPlayerIdx() against the current simulation agent.
            Move actualMove = move;
            if(move != null)
            {
                Agent currentAgent = copyGame.getCurrentAgent();
                if(move.getNewColorIfWild() != null)
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

    public ExpectedOutcomeAgentCopy(final int playerIdx,
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
        Node root = new MCTSNode(game, getLogicalPlayerIdx(), null);
        long startTime = System.currentTimeMillis();
        long timeLimit = Math.max(1L, this.getMaxThinkingTimeInMS() - 50L);

        if(root.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES)
        {
            while (System.currentTimeMillis() - startTime < timeLimit)
            {
                Node copyRoot = root;
                Move playMove = null;
                int firstActionIdx = -1;
                boolean isFirst = true;

                // Keep your overall design: walk down to an artificial leaf with random actions.
                while(!copyRoot.isTerminal() &&
                      copyRoot.getOrderedLegalMoves().size() > 0 &&
                      copyRoot.getDepth() < 5)
                {
                    int randomIdx = this.getRandom().nextInt(copyRoot.getOrderedLegalMoves().size());
                    HandView myHand = copyRoot.getGameView().getHandView(copyRoot.getLogicalPlayerIdx());
                    int handIdx = copyRoot.getOrderedLegalMoves().get(randomIdx);

                    // Important fix: orderedLegalMoves stores hand indices already.
                    // randomIdx is only the position inside that list.
                    Card card = myHand.getCard(handIdx);

                    if(card.isWild())
                    {
                        playMove = Move.createMove(this, handIdx, Color.getRandomColor(this.getRandom()));
                    }
                    else
                    {
                        playMove = Move.createMove(this, handIdx);
                    }

                    if(isFirst)
                    {
                        firstActionIdx = randomIdx;
                        isFirst = false;
                    }

                    copyRoot = copyRoot.getChild(playMove);
                }

                if(firstActionIdx == -1)
                {
                    continue;
                }

                Agent[] agents = new Agent[copyRoot.getGameView().getNumPlayers()];
                for(int logicalIdx = 0; logicalIdx < agents.length; logicalIdx++)
                {
                    int playerIdx = copyRoot.getGameView().getPlayerOrder().getAgentIdx(logicalIdx);
                    agents[logicalIdx] = new RandomAgent(playerIdx, 1L);
                }

                Game simGame = new Game(copyRoot.getGameView(), agents);
                int moveCount = 0;
                int rolloutCap = 60;

                // Let the framework + random agents handle the rollout from the leaf.
                // This avoids hand-writing opponent moves with the wrong playerIdx.
                while(!simGame.isOver() && moveCount < rolloutCap)
                {
                    Move randomMove = simGame.getMove();
                    simGame.resolveMove(randomMove);
                    moveCount++;
                }

                // Do NOT use Node.getUtilityValues() here.
                // On a fresh node with no q-counts it is 0/empty, not a rollout reward.
                float r = 0.0f;
                int myCards = simGame.getHand(getLogicalPlayerIdx()).size();
                if(simGame.isOver())
                {
                    r = (myCards == 0) ? 1.0f : -1.0f;
                }
                else
                {
                    int otherCards = 0;
                    for(int logicalIdx = 0; logicalIdx < simGame.getNumPlayers(); logicalIdx++)
                    {
                        if(logicalIdx != getLogicalPlayerIdx())
                        {
                            otherCards += simGame.getHand(logicalIdx).size();
                        }
                    }
                    r = otherCards - myCards;
                }

                float newTotal = root.getQValueTotal(firstActionIdx) + r;
                long newCount = root.getQCount(firstActionIdx) + 1;
                root.setQValueTotal(firstActionIdx, newTotal);
                root.setQCount(firstActionIdx, newCount);
            }
        }
        else if(root.getNodeState() == Node.NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD)
        {
            while (System.currentTimeMillis() - startTime < timeLimit)
            {
                // Branch 1: play the drawn card
                Agent[] playAgents = new Agent[game.getNumPlayers()];
                for(int logicalIdx = 0; logicalIdx < playAgents.length; logicalIdx++)
                {
                    int playerIdx = game.getPlayerOrder().getAgentIdx(logicalIdx);
                    playAgents[logicalIdx] = new RandomAgent(playerIdx, 1L);
                }

                Game playGame = new Game(game, playAgents);
                HandView myHand = game.getHandView(getLogicalPlayerIdx());
                Card drawnCard = myHand.getCard(drawnCardIdx);
                Agent currentAgent = playGame.getCurrentAgent();
                Move playMove = null;
                if(drawnCard.isWild())
                {
                    playMove = Move.createMove(currentAgent, drawnCardIdx, Color.getRandomColor(this.getRandom()));
                }
                else
                {
                    playMove = Move.createMove(currentAgent, drawnCardIdx);
                }
                playGame.resolveMove(playMove);

                int playCount = 0;
                while(!playGame.isOver() && playCount < 60)
                {
                    Move randomMove = playGame.getMove();
                    playGame.resolveMove(randomMove);
                    playCount++;
                }

                float playReward = 0.0f;
                int myCards = playGame.getHand(getLogicalPlayerIdx()).size();
                if(playGame.isOver())
                {
                    playReward = (myCards == 0) ? 1.0f : -1.0f;
                }
                else
                {
                    int otherCards = 0;
                    for(int logicalIdx = 0; logicalIdx < playGame.getNumPlayers(); logicalIdx++)
                    {
                        if(logicalIdx != getLogicalPlayerIdx())
                        {
                            otherCards += playGame.getHand(logicalIdx).size();
                        }
                    }
                    playReward = otherCards - myCards;
                }

                root.setQValueTotal(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX,
                                    root.getQValueTotal(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX) + playReward);
                root.setQCount(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX,
                               root.getQCount(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX) + 1);

                // Branch 2: keep the drawn card
                Agent[] keepAgents = new Agent[game.getNumPlayers()];
                for(int logicalIdx = 0; logicalIdx < keepAgents.length; logicalIdx++)
                {
                    int playerIdx = game.getPlayerOrder().getAgentIdx(logicalIdx);
                    keepAgents[logicalIdx] = new RandomAgent(playerIdx, 1L);
                }

                Game keepGame = new Game(game, keepAgents);

                // resolveMove(null) means "do not play a card", which is exactly the keep-card branch.
                keepGame.resolveMove(null);

                int keepCount = 0;
                while(!keepGame.isOver() && keepCount < 60)
                {
                    Move randomMove = keepGame.getMove();
                    keepGame.resolveMove(randomMove);
                    keepCount++;
                }

                float keepReward = 0.0f;
                int keepMyCards = keepGame.getHand(getLogicalPlayerIdx()).size();
                if(keepGame.isOver())
                {
                    keepReward = (keepMyCards == 0) ? 1.0f : -1.0f;
                }
                else
                {
                    int otherCards = 0;
                    for(int logicalIdx = 0; logicalIdx < keepGame.getNumPlayers(); logicalIdx++)
                    {
                        if(logicalIdx != getLogicalPlayerIdx())
                        {
                            otherCards += keepGame.getHand(logicalIdx).size();
                        }
                    }
                    keepReward = otherCards - keepMyCards;
                }

                root.setQValueTotal(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX,
                                    root.getQValueTotal(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX) + keepReward);
                root.setQCount(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX,
                               root.getQCount(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX) + 1);
            }
        }
        else
        {
            while (System.currentTimeMillis() - startTime < timeLimit)
            {
                Agent[] agents = new Agent[game.getNumPlayers()];
                for(int logicalIdx = 0; logicalIdx < agents.length; logicalIdx++)
                {
                    int playerIdx = game.getPlayerOrder().getAgentIdx(logicalIdx);
                    agents[logicalIdx] = new RandomAgent(playerIdx, 1L);
                }

                Game simGame = new Game(game, agents);

                // Here there is only one child: absorb the unresolved draw-card effect.
                Move forcedMove = simGame.getMove();
                simGame.resolveMove(forcedMove);

                int moveCount = 0;
                while(!simGame.isOver() && moveCount < 60)
                {
                    Move randomMove = simGame.getMove();
                    simGame.resolveMove(randomMove);
                    moveCount++;
                }

                float r = 0.0f;
                int myCards = simGame.getHand(getLogicalPlayerIdx()).size();
                if(simGame.isOver())
                {
                    r = (myCards == 0) ? 1.0f : -1.0f;
                }
                else
                {
                    int otherCards = 0;
                    for(int logicalIdx = 0; logicalIdx < simGame.getNumPlayers(); logicalIdx++)
                    {
                        if(logicalIdx != getLogicalPlayerIdx())
                        {
                            otherCards += simGame.getHand(logicalIdx).size();
                        }
                    }
                    r = otherCards - myCards;
                }

                root.setQValueTotal(Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX,
                                    root.getQValueTotal(Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX) + r);
                root.setQCount(Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX,
                               root.getQCount(Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX) + 1);
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
        Node.NodeState state = node.getNodeState();
        if(state == Node.NodeState.HAS_LEGAL_MOVES)
        {
            float value = Float.NEGATIVE_INFINITY;
            int valueIndex = 0;
            for(int i =0; i< node.getOrderedLegalMoves().size(); i++)
            {
                if(node.getQCount(i) > 0 && node.getQValue(i) > value)
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
        {
            float play = Float.NEGATIVE_INFINITY;
            float keep = Float.NEGATIVE_INFINITY;

            if(node.getQCount(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX) > 0)
            {
                play = node.getQValue(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX);
            }
            if(node.getQCount(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX) > 0)
            {
                keep = node.getQValue(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX);
            }

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
        else
        {
            // Unresolved draw cards have only one q-slot and no voluntary play choice.
            return null;
        }
    }
}
