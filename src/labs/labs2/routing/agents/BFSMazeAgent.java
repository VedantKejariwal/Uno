package src.labs.routing.agents;



// SYSTEM IMPORTS
import edu.bu.labs.routing.Coordinate;
import edu.bu.labs.routing.Direction;
import edu.bu.labs.routing.Path;
import edu.bu.labs.routing.State.StateView;
import edu.bu.labs.routing.Tile;
import edu.bu.labs.routing.agents.MazeAgent;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;       // will need for bfs
import java.util.Queue;         // will need for bfs
import java.util.LinkedList;    // will need for bfs
import java.util.Set;


// JAVA PROJECT IMPORTS


public class BFSMazeAgent
    extends MazeAgent
{

    public BFSMazeAgent(final int agentId)
    {
        super(agentId);
        System.out.println("hello world");
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
        Path<Coordinate> start = new Path<>(src);
        //Path<Coordinate> p = new Path<>(start, goal, 1d);
        Queue<Path<Coordinate>> q = new ArrayDeque<>();
        Set<Coordinate> visited = new HashSet<>();
        Path<Coordinate> u;
        visited.add(src);
        q.add(start);
        while(!q.isEmpty())
        {
            u = q.remove();
            if (u.current().equals(goal))
            {
                return u;
            }
            Direction[] dirs = Direction.values();
            for(int i =0; i <dirs.length; i++)
            {
                Direction dir = dirs[i];
                Coordinate cur = u.current();
                int newRow = cur.row() + dir.getDy();
                int newCol = cur.col() + dir.getDx();

                int rows = stateView.getNumRows();
                int cols = stateView.getNumCols();

                if(newRow < 0 || newRow >= rows || newCol < 0 || newCol >= cols)
                {
                    continue;
                }
                Coordinate nxt = new Coordinate(newRow, newCol);

                if(visited.contains(nxt))
                {
                    continue;
                }

                Tile.State tile = stateView.getTileState(nxt);
                if(tile == Tile.State.WALL)
                {
                    continue;
                }
                visited.add(nxt);
                q.add(new Path<>(u, nxt, 1d));
            }
        }
        return null;
    }

}
