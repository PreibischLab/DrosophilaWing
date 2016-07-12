package wt.alignment;

import java.util.Comparator;

import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.RealType;

public class PointComparator< T extends RealType< T > > implements Comparator< Point >
{
	final RandomAccess< T > r;

	public PointComparator( final RandomAccessible< T > img )
	{
		this.r = img.randomAccess();
	}

	@Override
	public int compare( final Point o1, final Point o2 )
	{
		r.setPosition( o1 );
		final double v1 = r.get().getRealDouble();
		r.setPosition( o2 );
		final double v2 = r.get().getRealDouble();

		if ( v1 < v2 )
			return -1;
		else if ( v1 == v2 )
			return 0;
		else
			return 1;
	}
}
