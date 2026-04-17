package src.labs.lab1.agents;


import edu.bu.labs.lab1.Coordinate;
import edu.bu.labs.lab1.Direction;
import edu.bu.labs.lab1.State.StateView;
import edu.bu.labs.lab1.Tile;
import edu.bu.labs.lab1.agents.Agent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


// JAVA PROJECT IMPORTS


public class ClosestUnitAgent
    extends Agent
{

    // put your fields here! You will probably want to remember the following information:
    //      - all friendly unit ids (there may be more than one!)
    //      - the location(s) of COIN(s) on the map

    private Set<Integer> myUnitIds;
    private Coordinate exitLocation;

    /**
     * The constructor for this type. Each agent has a unique ID that you will need to use to request info from the
     * state about units it controls, etc.
     */
	public ClosestUnitAgent(final int agentId)
	{
		super(agentId); // make sure to call parent type (Agent)'s constructor!

        // initialize your fields here!

        this.myUnitIds = new HashSet<>();
        this.exitLocation = null;

        // helpful printout just to help debug
		System.out.println("Constructed ClosestUnitAgent");
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
        for (Integer unitId : stateView.getUnitIds(this.getAgentId()))
        {
            this.myUnitIds.add(unitId);
        }

        int numRows = stateView.getNumRows();
        int numCols = stateView.getNumCols();
        for (int row = 0; row < numRows; row++)
        {
            for (int col = 0; col < numCols; col++)
            {
                if (stateView.getTileState(new Coordinate(row, col)) == Tile.State.FINISH)
                {
                    this.exitLocation = new Coordinate(row, col);
                }
            }
        }
	}

    /**
     * This method is called every turn (or "frame") of the game. Your agent is responsible for assigning
     * actions to each of the unit(s) your agent controls. The return type of this method is a mapping
     * from unit ID (that your agent controls) to the Direction you want that unit to move in.
     *
     * If you are trying to collect COIN(s), you do so by walking into the same square as a COIN. Your agent
     * will pick it up automatically (and the COIN will dissapear from the map).
     */
	@Override
	public Map<Integer, Direction> assignActions(final StateView state)
    {
        Map<Integer, Direction> actions = new HashMap<>();

        if (this.exitLocation == null || this.myUnitIds.isEmpty())
        {
            return actions;
        }

        Integer closestUnit = null;
        long bestDistance = Long.MAX_VALUE;

        for (Integer unitId : this.myUnitIds)
        {
            Coordinate pos = state.getUnitView(this.getAgentId(), unitId).currentPosition();
            int dx = pos.col() - this.exitLocation.col();
            int dy = pos.row() - this.exitLocation.row();
            long dist = (long) dx * dx + (long) dy * dy;

            if (dist < bestDistance)
            {
                bestDistance = dist;
                closestUnit = unitId;
            }
        }

        if (closestUnit == null)
        {
            return actions;
        }

        Coordinate pos = state.getUnitView(this.getAgentId(), closestUnit).currentPosition();
        Direction move = null;

        if (pos.col() < this.exitLocation.col())
        {
            move = Direction.RIGHT;
        }
        else if (pos.col() > this.exitLocation.col())
        {
            move = Direction.LEFT;
        }
        else if (pos.row() < this.exitLocation.row())
        {
            move = Direction.DOWN;
        }
        else if (pos.row() > this.exitLocation.row())
        {
            move = Direction.UP;
        }

        if (move != null)
        {
            actions.put(closestUnit, move);
        }

        return actions;
	}

}

