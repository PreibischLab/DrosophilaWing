package wt.tessellation;

import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessible;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.neighborsearch.NearestNeighborSearchInterpolatorFactory;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.view.Views;

public class Search< S extends Segment >
{
	// point list
	final IterableRealInterval< S > realInterval;

	// the factory
	final NearestNeighborSearchInterpolatorFactory< S > factory = new NearestNeighborSearchInterpolatorFactory< S >();

	// for searching
	KDTree< S > kdTree;

	// using nearest neighbor search we will be able to return a value an any position in space
	NearestNeighborSearch< S > search;

	// make it into RealRandomAccessible using nearest neighbor search
	RealRandomAccessible< S > realRandomAccessible;

	// convert it into a RandomAccessible which can be displayed
	RandomAccessible< S > randomAccessible;

	public Search( final IterableRealInterval< S > realInterval )
	{
		this.realInterval = realInterval;

		update();
	}

	public void update()
	{
		this.kdTree = new KDTree< S > ( realInterval );
		this.search = new NearestNeighborSearchOnKDTree< S >( kdTree );
		this.realRandomAccessible = Views.interpolate( search, factory );
		this.randomAccessible = Views.raster( realRandomAccessible );
	}

	public KDTree< S > kdTree() { return kdTree; }
	public IterableRealInterval< S > segments() { return realInterval; }
	public RandomAccessible< S > randomAccessible() { return randomAccessible; }
}
