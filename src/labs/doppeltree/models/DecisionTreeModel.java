package labs.doppeltree.models;

// SYSTEM IMPORTS
import edu.bu.jmat.Matrix;
import edu.bu.jmat.Pair;
import edu.bu.labs.doppeltree.features.*;
import edu.bu.labs.doppeltree.enums.*;
import edu.bu.labs.doppeltree.models.Model;

// JAVA PROJECT IMPORTS
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DecisionTreeModel extends Model 
{

    // an abstract Node type. This is extended to make Interior Nodes and Leaf Nodes
    public static abstract class Node 
    extends Object 
    {

        // the dataset that was used to construct this node
        private Matrix X;
        private Matrix y_gt;
        private FeatureHeader featureHeader;

        public Node(final Matrix X, final Matrix y_gt, final FeatureHeader featureHeader) {
            this.X = X;
            this.y_gt = y_gt;
            this.featureHeader = featureHeader;
        }

        public final Matrix getX() { return this.X; }
        public final Matrix getY() { return this.y_gt; }
        public final FeatureHeader getFeatureHeader() { return this.featureHeader; }

        // a method to get the majority class (i.e. the most popular class) from ground truth.
        public int getMajorityClass(final Matrix X, final Matrix y_gt) {
            Pair<Matrix, Matrix> uniqueYGtAndCounts = y_gt.unique();
            Matrix uniqueYGtVals = uniqueYGtAndCounts.first();
            Matrix counts = uniqueYGtAndCounts.second();

            // find the argmax of the counts
            int rowIdxOfMaxCount = -1;
            double maxCount = Double.NEGATIVE_INFINITY;

            for(int rowIdx = 0; rowIdx < counts.getShape().numRows(); ++rowIdx) {
                if(counts.get(rowIdx, 0) > maxCount) {
                    rowIdxOfMaxCount = rowIdx;
                    maxCount = counts.get(rowIdx, 0);
                }
            }
            return (int)uniqueYGtVals.get(rowIdxOfMaxCount, 0);
        }

        // an abstract method to predict the class for this example
        public abstract int predict(final Matrix x);
        // an abstract method to get the datasets that each child node should be built from
        public abstract List<Pair<Matrix, Matrix>> getChildData() throws Exception;
    }

    // leaf node type
    public static class LeafNode 
    extends Node {

        // a leaf node has the class label inside it
        private int predictedClass;

        public LeafNode(final Matrix X, final Matrix y_gt, final FeatureHeader featureHeader) {
            super(X, y_gt, featureHeader);
            this.predictedClass = this.getMajorityClass(X, y_gt);
        }

        @Override
        public int predict(final Matrix x) {
            // predict the class (an integer)
            return this.predictedClass;
        }

        // leaf nodes have no children
        @Override
        public List<Pair<Matrix, Matrix>> getChildData() throws Exception { 
            return null; 
        }
    }

    // interior node type
    public static class InteriorNode 
    extends Node {

        // the column index of the feature that this interior node has chosen
        private int             featureIdx;

        // the type (continuous or discrete) of the feature this interior node has chosen
        private FeatureType     featureType;

        // when we're processing a discrete feature, it is possible that even though that discrete feature
        // can take on any value in its domain (for example, like 5 values), the data we have may not contain
        // all of those values in it. Therefore, whenever we want to predict a test point, it is possible
        // that the test point has a discrete value that we haven't seen before. When we encounter such scenarios
        // we should predict the majority class (aka assign an "out-of-bounds" leaf node)
        private int             majorityClass;

        // the values of the feature that identify each child
        // if the feature this node has chosen is discrete, then |splitValues| = |children|
        // if the feature this node has chosen is continuous, then |splitValues| = 1 and |children| = 2
        private List<Double>    splitValues; 
        private List<Node>      children;

        // what features are the children of this node allowed to use?
        // this is different if the feature this node has chosen is discrete or continuous
        private Set<Integer>    childColIdxs;

        public InteriorNode(final Matrix X, 
                            final Matrix y_gt, 
                            final FeatureHeader featureHeader, 
                            final Set<Integer> availableColIdxs) 
                            {
            super(X, y_gt, featureHeader);
            this.splitValues = new ArrayList<Double>();
            this.children = new ArrayList<Node>();
            this.majorityClass = this.getMajorityClass(X, y_gt);

            // make a deepcopy of the set that is given to us....we need to potentially remove stuff from this
            // so don't use a shallow copy and risk messing up parent nodes (with a shared shallow copy)!
            this.childColIdxs = new HashSet<Integer>(availableColIdxs);

            // quite a lot happens in this method.
            // this method will figure out which feature (amongst all the ones that we are allowed to see)
            // has the "best" quality (as measured by info gain). It will also populate the field 'this.splitValues'
            // with the correct values for that feature.
            // (side note: this is why this method is being called *after* this.splitValues is initialized)

            this.featureIdx = this.pickBestFeature(X, y_gt, availableColIdxs);
            if (this.featureIdx != -1) 
                {
                this.featureType = this.getFeatureHeader().getFeature(this.getFeatureIdx()).getFeatureType();

            // once we know what feature this node has, we need to remove that feature from our children
            // if that feature is discrete.
            // we made a deepcopy of the set so we're all good to in-place remove here.

                if(this.getFeatureType().equals(FeatureType.DISCRETE)) 
                    {
                    this.getChildColIdxs().remove(this.getFeatureIdx());
                }
            }
        }

        //------------------------ some getters and setters (cause this is java) ------------------------
        public int getFeatureIdx() { return this.featureIdx; }
        public final FeatureType getFeatureType() { return this.featureType; }

        private List<Double> getSplitValues() { return this.splitValues; }
        private List<Node> getChildren() { return this.children; }

        public Set<Integer> getChildColIdxs() { return this.childColIdxs; }
        public int getMajorityClass() { return this.majorityClass; }
        //-----------------------------------------------------------------------------------------------

        // make sure we add children in the correct order when we use this!
        public void addChild(final Node n) 
        { 
            this.getChildren().add(n); 
        }

        private double entropy(Matrix y) 
        {
            int n = y.getShape().numRows();
            if (n == 0) return 0.0;

            Pair<Matrix, Matrix> uc = y.unique();
            Matrix counts = uc.second();

            double h = 0.0;
            for (int r = 0; r < counts.getShape().numRows(); r++) 
                {
                double p = counts.get(r, 0) / n;
                if (p > 0) h -= p * (Math.log(p) / Math.log(2));
            }
            return h;
        }

        private Pair<Matrix, Matrix> filterData(Matrix X, Matrix y, int colIdx, double t, String op) 
        {
            int nr = X.getShape().numRows();
            int nc = X.getShape().numCols();
            List<Integer> rows = new ArrayList<>();

            for (int r = 0; r < nr; r++) 
                {
                double v = X.get(r, colIdx);
                boolean m = false;
                
                if (op.equals("==")) 
                    {
                         m = (v == t);
                        }
                else if (op.equals("<="))
                    {
                         m = (v <= t);
                    }
                else if (op.equals(">"))
                    {
                         m = (v > t);
                    }
                if (m) 
                    {
                        rows.add(r);
                    }
            }

            int m = rows.size();
            Matrix fX = Matrix.zeros(m, nc);
            Matrix fY = Matrix.zeros(m, 1);

            for (int i = 0; i < rows.size(); i++) 
                {
                int r = rows.get(i);
                for (int c = 0; c < nc; c++) 
                    {
                        fX.set(i, c, X.get(r, c));
                    }
                fY.set(i, 0, y.get(r, 0));
            }
            return new Pair<Matrix, Matrix>(fX, fY);
        }

        private Pair<Double, Matrix> getConditionalEntropy(final Matrix X, final Matrix y_gt, final int colIdx) throws Exception 
        {
            int n = X.getShape().numRows();
            FeatureType ft = this.getFeatureHeader().getFeature(colIdx).getFeatureType();
            
            Matrix col = Matrix.zeros(n, 1);
            for (int r = 0; r < n; r++) 
                {
                    col.set(r, 0, X.get(r, colIdx));
                }
            Matrix uVals = col.unique().first();

            if (ft.equals(FeatureType.DISCRETE)) 
                {
                double ch = 0.0;
                for (int i = 0; i < uVals.getShape().numRows(); i++) 
                    {
                    double v = uVals.get(i, 0);
                    Pair<Matrix, Matrix> split = filterData(X, y_gt, colIdx, v, "==");

                    double w = (double) split.first().getShape().numRows() / n;
                    ch += w * entropy(split.second());
                }

                return new Pair<Double, Matrix>(ch, uVals);

            } 
            else 
                {
                double bh = Double.POSITIVE_INFINITY;
                double bt = uVals.get(0, 0);

                List<Double> sv = new ArrayList<>();

                for (int i = 0; i < uVals.getShape().numRows(); i++) 
                    {
                        sv.add(uVals.get(i, 0));
                    }
                Collections.sort(sv);

                for (double t : sv) 
                    {
                    Pair<Matrix, Matrix> left = filterData(X, y_gt, colIdx, t, "<=");
                    Pair<Matrix, Matrix> right = filterData(X, y_gt, colIdx, t, ">");

                    int nl = left.first().getShape().numRows();
                    int nr = right.first().getShape().numRows();

                    if (nl == 0 || nr == 0) 
                        {
                            continue;
                        }
                    
                    double h = ((double) nl / n) * entropy(left.second()) + ((double) nr / n) * entropy(right.second());
                    if (h < bh) 
                        {
                        bh = h;
                        bt = t;
                    }
                }
                return new Pair<Double, Matrix>(bh, Matrix.full(1, 1, bt));
            }
        }

        private int pickBestFeature(final Matrix X, final Matrix y_gt, final Set<Integer> availableColIdxs) 
        {
            double ph = entropy(y_gt);
            double big = 0.0;

            int bc = -1;
            Matrix bs = null;
            
            List<Integer> sc = new ArrayList<>(availableColIdxs);
            Collections.sort(sc);

            for (int c : sc) 
                {
                try 
                {
                    Pair<Double, Matrix> res = getConditionalEntropy(X, y_gt, c);
                    double ig = ph - res.first();

                    if (ig > big)
                        {
                        big = ig;
                        bc = c;
                        bs = res.second();
                    }
                } 
                catch (Exception e) {}
            }

            if (bc != -1 && bs != null) 
                {
                for (int r = 0; r < bs.getShape().numRows(); r++) 
                    {
                    this.getSplitValues().add(bs.get(r, 0));
                }
            }
            return bc;
        }

        @Override
        public int predict(final Matrix x) 
        {
            double v = x.get(0, this.getFeatureIdx());

            if (this.getFeatureType().equals(FeatureType.CONTINUOUS)) 
                {
                if (v <= this.getSplitValues().get(0)) 
                    {
                        return this.getChildren().get(0).predict(x);
                    }
                return this.getChildren().get(1).predict(x);
            } 
            else 
                {
                for (int i = 0; i < this.getSplitValues().size(); i++) 
                    {
                    if (v == this.getSplitValues().get(i)) 
                        {
                            return this.getChildren().get(i).predict(x);
                        }
                }
                return this.getMajorityClass();
            }
        }

        @Override
        public List<Pair<Matrix, Matrix>> getChildData() throws Exception 
        {
            List<Pair<Matrix, Matrix>> kids = new ArrayList<>();
            Matrix X = this.getX();
            Matrix y = this.getY();

            if (this.getFeatureType().equals(FeatureType.CONTINUOUS)) 
                {
                double t = this.getSplitValues().get(0);
                kids.add(filterData(X, y, this.getFeatureIdx(), t, "<="));
                kids.add(filterData(X, y, this.getFeatureIdx(), t, ">"));
            } 
            else 
                {
                for (double v : this.getSplitValues()) 
                    {
                    kids.add(filterData(X, y, this.getFeatureIdx(), v, "=="));
                }
            }
            return kids;
        }
    }

    private Node root;

    public DecisionTreeModel(final FeatureHeader featureHeader) 
    {
        super(featureHeader);
        this.root = null;
    }

    public Node getRoot() 
    { 
        return this.root; 
    }
    private void setRoot(Node n) 
    { 
        this.root = n; 
    }

    private Node dfsBuild(Matrix X, Matrix y_gt, Set<Integer> availableColIdxs) throws Exception 
    {
        if (X.getShape().numRows() == 0 || availableColIdxs.isEmpty()) 
            {
            return new LeafNode(X, y_gt, this.getFeatureHeader());
        }
        if (y_gt.unique().first().getShape().numRows() == 1) 
            {
            return new LeafNode(X, y_gt, this.getFeatureHeader());
        }

        InteriorNode node = new InteriorNode(X, y_gt, this.getFeatureHeader(), availableColIdxs);

        if (node.getFeatureIdx() == -1) 
            {
            return new LeafNode(X, y_gt, this.getFeatureHeader());
        }

        for (Pair<Matrix, Matrix> kid : node.getChildData())
             {
            Matrix kX = kid.first();
            Matrix kY = kid.second();
            
            if (kX.getShape().numRows() == 0) 
                {
                node.addChild(new LeafNode(X, y_gt, this.getFeatureHeader()));
            } 
            else 
                {
                node.addChild(dfsBuild(kX, kY, node.getChildColIdxs()));
            }
        }
        return node;
    }
    // did this for you, feel free to change the printouts if you want
    @Override
    public void train(final Matrix trainFeatures, 
        final Matrix trainGroundTruth) 
        {
        System.out.println("DecisionTree.fit: X.shape=" + trainFeatures.getShape() + 
        " y_gt.shape=" + trainGroundTruth.getShape());
        
        try 
        {
            Set<Integer> allColIdxs = new HashSet<Integer>();
            for(int colIdx = 0; colIdx < trainFeatures.getShape().numCols(); ++colIdx) 
                {
                allColIdxs.add(colIdx);
            }

            this.setRoot(this.dfsBuild(trainFeatures, trainGroundTruth, allColIdxs));
        } 
        catch(Exception e) 
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    // did this for you, feel free to change the printouts if you want
    @Override
    public int classify(final Matrix featureVec) 
    {
        // class 0 means Human (i.e. not a zombie), class 1 means zombie
        System.out.println("DecisionTree.predict: x=" + featureVec);
        return this.getRoot().predict(featureVec);
    }
}
