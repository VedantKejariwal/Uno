package labs.labs1.lab1.agents;


// SYSTEM IMPORTS
import edu.bu.labs.lab1.Direction;
import edu.bu.labs.lab1.State.StateView;
import edu.bu.labs.lab1.agents.Agent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


// JAVA PROJECT IMPORTS


public class ZigZagAgent
    extends Agent
{

    // put your fields here! You will probably want to remember the following information:
    //      - all friendly unit ids (there may be more than one!)
    //      - the location(s) of COIN(s) on the map

    /**
     * The constructor for this type. Each agent has a unique ID that you will need to use to request info from the
     * state about units it controls, etc.
     */

    private Set<Integer> myUnitIds;
    private boolean moveRightNext;

	public ZigZagAgent(final int agentId)
	{
		super(agentId); // make sure to call parent type (Agent)'s constructor!

        // initialize your fields here!
        this.myUnitIds = new HashSet<>();
        this.moveRightNext = true;

        // helpful printout just to help debug
		System.out.println("Constructed ZigZagAgent");
	}

    /////////////////////////////// GETTERS AND SETTERS (this is Java after all) ///////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * This method is called by our game engine once: before any moves are made. You are provided with the state of
     * the game before any actions have been taken. This is in case you have some fields you need to set but are
     * unable to in the constructor of this class (like keeping track of units on the map, etc.).
     */
	@Override
	public void initializeFromState(final StateView stateView)
	{
        // TODO: identify units, set fields that couldn't be initialized in the constructor because
        // of a lack of game data in the constructor.
        // discover my unitId

        for(Integer unitId : stateView.getUnitIds(this.getAgentId()))
        {
            myUnitIds.add(unitId);
        }
	}

    /**
     * This method is called every turn (or "frame") of the game. Your agent is responsible for assigning
     * actions to each of the unit(s) your agent controls. The return type of this method is a mapping
     * from unit ID (that your agent controls) to the Direction you want that unit to move in.
     *
     * If you are trying to collect COIN(s), you do so by walking into the same square as a COIN. Your agent
     * will pick it up automatically (and the COIN will disappear from the map).
     */
	@Override
	public Map<Integer, Direction> assignActions(final StateView state)
    {
        Map<Integer, Direction> actions = new HashMap<>();

        // TODO: your code to give your unit(s) actions for this turn goes here!

        Direction move;
        if(moveRightNext)
        {
            move = Direction.RIGHT;
        }
        else
        {
            move = Direction.UP;
        }

        for(Integer unitId : myUnitIds)
        {
            actions.put(unitId, move);
        }

        moveRightNext = !moveRightNext;

        return actions;
	}

}

