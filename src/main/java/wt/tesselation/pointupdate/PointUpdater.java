package wt.tesselation.pointupdate;

import java.util.Collection;

import net.imglib2.RealPoint;

public interface PointUpdater
{
	public void updatePoints( final RealPoint p, final Collection< RealPoint > allpoints, final double dx, final double dy );
}
