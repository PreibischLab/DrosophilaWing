package wt.alignment;

import java.util.ArrayList;

import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.RealType;

public class Quicksort< T extends RealType< T > >
{
	final RandomAccessible< T > image;
	final ArrayList< Point > positionList;
	//final ArrayList<Integer> indexList;
	final int[] indexList;
	final RandomAccess< T > r;
	final int n;

	public Quicksort( final RandomAccessible< T > image, final ArrayList< Point > positionList)
	{
		this.n = image.numDimensions();
		this.positionList = positionList;
		this.image = image;
		this.r = image.randomAccess();
		//this.indexList = new ArrayList<Integer>(positionList.size());
		this.indexList = new int[ positionList.size() ];

		for (int i = 0; i < positionList.size() ; ++i )
			indexList[ i ] = i;;
	}
	
	public int[] qsort()
	{
		qs( 0, positionList.size() - 1);
		return indexList;
	}
	
	private void qs( final int left, final int right)
	{
		int i, j;
		double pivot;
		i = left;
		j = right;

		r.setPosition( 0, 0 );
		pivot = accessor((left + right) / 2).getRealDouble();
		
		do {
			while (accessor(i).getRealDouble() < pivot && (i < right)) { i++; }
			
			while ((pivot < accessor(j).getRealDouble()) && (j > left)) { j--; }
				
			if (i <= j)
			{
				final int temp = indexList[ i ];
				indexList[ i ] = indexList[ j ];
				indexList[ j ] = temp;
				i++;
				j--;
			}
		}  while (i <= j);
			
		if (left < j)
			qs (left, j);

		if (i < right)
			qs (i, right);
	}

	public T accessor( final int i )
	{
		r.setPosition( positionList.get( indexList[ i ] ) );
		return r.get();
	}
}
