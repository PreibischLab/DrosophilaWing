package wt.tesselation;

import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessible;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.neighborsearch.NearestNeighborSearchInterpolatorFactory;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.view.Views;

public class Search
{
	// point list
	final IterableRealInterval< Segment > realInterval;

	// the factory
	final NearestNeighborSearchInterpolatorFactory< Segment > factory = new NearestNeighborSearchInterpolatorFactory< Segment >();

	// for searching
	KDTree< Segment > kdTree;

	// using nearest neighbor search we will be able to return a value an any position in space
	NearestNeighborSearch< Segment > search;

	// make it into RealRandomAccessible using nearest neighbor search
	RealRandomAccessible< Segment > realRandomAccessible;

	// convert it into a RandomAccessible which can be displayed
	RandomAccessible< Segment > randomAccessible;

	public Search( IterableRealInterval< Segment > realInterval )
	{
		this.realInterval = realInterval;

		update();
	}

	public void update()
	{
		this.kdTree = new KDTree< Segment > ( realInterval );
		this.search = new NearestNeighborSearchOnKDTree< Segment >( kdTree );
		this.realRandomAccessible = Views.interpolate( search, factory );
		this.randomAccessible = Views.raster( realRandomAccessible );
	}
}
