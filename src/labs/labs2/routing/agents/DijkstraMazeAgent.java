package src.labs.routing.agents;

// SYSTEM IMPORTS
import edu.bu.labs.routing.Coordinate;
import edu.bu.labs.routing.Direction;
import edu.bu.labs.routing.Path;
import edu.bu.labs.routing.State.StateView;
import edu.bu.labs.routing.Tile;
import edu.bu.labs.routing.agents.MazeAgent;


import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue; // heap in java
import java.util.Set;


// JAVA PROJECT IMPORTS


public class DijkstraMazeAgent
    extends MazeAgent
{

    public DijkstraMazeAgent(final int agentId)
    {
        super(agentId);
    }

    public double getEdgeWeight(final Coordinate src,
                                final Coordinate dst,
                                final StateView stateView)
    {
        // TODO: alter me to account for risk!
        return 1d;
    }

    @Override
    public void initializeFromState(final StateView stateView)
    {
        // find the FINISH tile
        Coordinate finishCoord = null;
        for(int rowIdx = 0; rowIdx < stateView.getNumRows(); ++rowIdx)
        {
            for(int colIdx = 0; colIdx < stateView.getNumCols(); ++colIdx)
            {
                if(stateView.getTileState(new Coordinate(rowIdx, colIdx)) == Tile.State.FINISH)
                {
                    finishCoord = new Coordinate(rowIdx, colIdx);
                }
            }
        }
        this.setFinishCoordinate(finishCoord);

        // make sure to call the super-class' version!
        super.initializeFromState(stateView);
    }

    @Override
    public boolean shouldReplacePlan(final StateView stateView)
    {
        return false;
    }

    @Override
    public Path<Coordinate> search(final Coordinate src,
                                   final Coordinate goal,
                                   final StateView stateView)
    {
        // TODO: complete me!
		return null;
    }

}
