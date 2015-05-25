package wt.tessellation.error;

import wt.tessellation.Segment;


public interface Error
{
	public double computeError( final Iterable< Segment > segmentMap, final double target );
}
