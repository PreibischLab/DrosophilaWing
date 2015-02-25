package wt.tesselation;


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
				error += ( 100 * target ) * ( 100 * target );
			else
				error += ( area - target ) * ( area - target );
		}
		
		return error;
	}
}
