package src.pas.pacman.routing;


// SYSTEM IMPORTS
import java.util.Collection;


// JAVA PROJECT IMPORTS
import edu.bu.pas.pacman.agents.Agent;
import edu.bu.pas.pacman.game.Action;
import edu.bu.pas.pacman.game.Game.GameView;
import edu.bu.pas.pacman.game.Tile;
import edu.bu.pas.pacman.graph.Path;
import edu.bu.pas.pacman.routing.BoardRouter;
import edu.bu.pas.pacman.routing.BoardRouter.ExtraParams;
import edu.bu.pas.pacman.utils.Coordinate;
import edu.bu.pas.pacman.utils.Pair;
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;       // will need for bfs
import java.util.Queue;         // will need for bfs
import java.util.LinkedList;    // will need for bfs
import java.util.Set;


// This class is responsible for calculating routes between two Coordinates on the Map.
// Use this in your PacmanAgent to calculate routes that (if followed) will lead
// Pacman from some Coordinate to some other Coordinate on the map.
public class ThriftyBoardRouter
    extends BoardRouter
{

    // If you want to encode other information you think is useful for Coordinate routing
    // besides Coordinates and data available in GameView you can do so here.
    public static class BoardExtraParams
        extends ExtraParams
    {

    }

    // feel free to add other fields here!

    public ThriftyBoardRouter(int myUnitId,
                              int pacmanId,
                              int ghostChaseRadius)
    {
        super(myUnitId, pacmanId, ghostChaseRadius);

        // if you add fields don't forget to initialize them here!
    }


    @Override
    public Collection<Coordinate> getOutgoingNeighbors(final Coordinate src,
                                                       final GameView game,
                                                       final ExtraParams params)
    {
        // TODO: implement me!
        List<Coordinate> xyz = new ArrayList<>();
        int newX; //new horizontal
        int newY; //new vertical
        newX = src.x();
        newY = src.y();
        Coordinate[] arr;
        arr = new Coordinate[] {
            new Coordinate(newX +1, newY),
            new Coordinate(newX - 1, newY), 
            new Coordinate(newX, newY +1), 
            new Coordinate(newX, newY-1)};
        for(int i=0; i<4; i++)
        {
        if(game.isInBounds(arr[i]) && game.getTile(arr[i]).getState() != Tile.State.WALL && game.getTile(arr[i]).getState() != Tile.State.GHOST_PEN )
        {
            //wall NOT is in coordinate src
            xyz.add(arr[i]);
        }
    }
        return xyz;
    }

    @Override
    public Path<Coordinate> graphSearch(final Coordinate src,
                                        final Coordinate tgt,
                                        final GameView game)
    {
        // TODO: implement me!
        Path<Coordinate> start = new Path<>(src);
        Queue<Path<Coordinate>> q = new ArrayDeque<>();
        Set<Coordinate> visited = new HashSet<>();
        Path<Coordinate> u;
        Coordinate nxt = null;
        if (tgt == null) System.out.println("graphSearch: TARGET IS NULL");
        
        visited.add(src);
        q.add(start);
        while(!q.isEmpty())
        {
            //System.out.println("graphSearch: q is not empty");
            u = q.remove();
            if (u.getDestination().equals(tgt))
            {
                //System.out.println("graphSearch: u.getDestination().equals(tgt)");
                return u;  
            }
            List<Coordinate> abc = new ArrayList<>(getOutgoingNeighbors(u.getDestination(), game, null));
            for(int i =0; i<abc.size(); i ++)
            {
                //System.out.println("graphSearch: in the for loop" + i);
                nxt = abc.get(i);
            
                if(visited.contains(nxt))
                {
                    continue;
                }
                
                visited.add(nxt);
                q.add(new Path<Coordinate>(nxt, 1.0f, u));
            }
        }
        return null;
    }

}

