package wt.alignment;

import java.util.ArrayList;

import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.RealType;

public class Median 
{
	public static < T extends RealType< T >> T median( final RandomAccessible< T > image, final ArrayList< Point > indexList )
	{
		if (indexList.size() == 0)
			return null;

		final Quicksort< T > qSortTmp = new Quicksort< T >(image, indexList);
		final int[] sortedIndex = qSortTmp.qsort();
		
		final T med;
		
		if (indexList.size() % 2 == 1)
		{
			med = qSortTmp.accessor(sortedIndex[ indexList.size() / 2 ]).copy();
		}
		else {
			int i = (indexList.size() / 2);
			med = qSortTmp.accessor(sortedIndex[ i ]).copy();
			med.setReal( ( med.getRealDouble() + qSortTmp.accessor(sortedIndex[i-1]).getRealDouble() ) / 2.0 );
			
		}
		System.out.println( "median : " + med );
		return med;
	}
	
}
