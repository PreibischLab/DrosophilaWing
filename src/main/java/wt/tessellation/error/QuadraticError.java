package wt.tessellation.error;

import wt.tessellation.Segment;


public class QuadraticError implements Error
{
	@Override
	public double computeError( final Iterable< Segment > segmentMap, final double target )
	{
		double error = 0;

		for ( final Segment s : segmentMap )
		{
			final int area = s.area();
			
			if ( area <= 1 )
				error += ( 1000 * target ) * ( 1000 * target );
			else
				error += ( area - target ) * ( area - target );
		}
		
		return error;
	}
}
