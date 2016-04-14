package wt.tools;

import java.util.ArrayList;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;

import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.type.Type;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import wt.quantify.localmaxima.RealPointValue;

public class LocalMaxima
{
	public static < T extends Comparable< T > & Type< T > > ArrayList< RealPointValue< T > > localMaxima(
			final RandomAccessibleInterval< T > img,
			final T minValue)
	{
		final ArrayList< RealPointValue< T > > points = new ArrayList< RealPointValue< T > >();

		// define an interval that is one pixel smaller on each side in each dimension,
		// so that the search in the 8-neighborhood (3x3x3...x3) never goes outside
		// of the defined interval
		final Interval interval = Intervals.expand( img, -1 );
		
		// create a view on the source with this interval
		final RandomAccessibleInterval< T > source = Views.interval( img, interval );
		
		// create a Cursor that iterates over the source and checks in a 8-neighborhood
		// if it is a minima
		final Cursor< T > center = Views.iterable( source ).cursor();

		// instantiate a RectangleShape to access rectangular local neighborhoods
		// of radius 1 (that is 3x3x...x3 neighborhoods), skipping the center pixel
		// (this corresponds to an 8-neighborhood in 2d or 26-neighborhood in 3d, ...)
		final RectangleShape shape = new RectangleShape( 1, true );
		
		// iterate over the set of neighborhoods in the image
		for ( final Neighborhood< T > localNeighborhood : shape.neighborhoods( source ) )
		{
			// what is the value that we investigate?
			// (the center cursor runs over the image in the same iteration order as neighborhood)
			final T centerValue = center.next();

			// at least that bright
			if ( centerValue.compareTo( minValue ) <= 0)
				continue;

			// keep this boolean true as long as no other value in the local neighborhood
			// is larger or equal
			boolean isMax = true;
			
			// check if all pixels in the local neighborhood that are smaller
			for ( final T value : localNeighborhood )
			{
				// test if the center is smaller than the current pixel value
				if ( centerValue.compareTo( value ) <= 0 )
				{
					isMax = false;
					break;
				}
			}
			
			if ( isMax )
				points.add( new RealPointValue< T >( center.get().copy(), center ) );
		}
		
		return points;
	}
}
