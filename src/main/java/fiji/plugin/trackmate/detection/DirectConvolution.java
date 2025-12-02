package fiji.plugin.trackmate.detection;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class DirectConvolution< T extends RealType< T > & NativeType< T > >
{
	private final RandomAccessibleInterval< T > image;

	private final Img< T > kernel;

	/**
	 * Creates a new DirectConvolution operator.
	 *
	 * @param image
	 * @param kernel
	 */
	public DirectConvolution(
			final RandomAccessibleInterval< T > image,
			final Img< T > kernel )
	{
		this.image = image;
		this.kernel = kernel;
	}

	/**
	 * Performs the direct convolution operation.
	 * The result is written back to the input image (in-place operation),
	 * maintaining compatibility with FFTConvolution behavior.
	 */
	public void convolve()
	{
		final int n = image.numDimensions();

		// Get kernel dimensions
		final long[] kernelDims = new long[ n ];
		kernel.dimensions( kernelDims );

		// Calculate kernel center
		final long[] kernelCenter = new long[ n ];
		for ( int d = 0; d < n; d++ )
		{
			kernelCenter[ d ] = kernelDims[ d ] / 2;
		}

		// Create temporary output image with same dimensions and type as input
		final T type = Views.iterable( image ).firstElement();
		final ImgFactory< T > factory = Util.getArrayOrCellImgFactory( image, type );
		final Img< T > output = factory.create( image );

		// Cursor for output image (with localization)
		final Cursor< T > outCursor = output.localizingCursor();

		// RandomAccess for input image (with mirror border extension)
		final RandomAccess< T > inAccess = Views.extendMirrorSingle( image ).randomAccess();

		// Cursor for kernel
		final Cursor< T > kernelCursor = kernel.localizingCursor();

		// Temporary position arrays
		final long[] pos = new long[ n ];
		final long[] kernelPos = new long[ n ];

		// For each output pixel
		while ( outCursor.hasNext() )
		{
			outCursor.fwd();
			outCursor.localize( pos );

			double sum = 0.0;

			// Reset kernel cursor
			kernelCursor.reset();

			// For each kernel element
			while ( kernelCursor.hasNext() )
			{
				kernelCursor.fwd();
				kernelCursor.localize( kernelPos );

				// Calculate position in input image
				for ( int d = 0; d < n; d++ )
				{
					inAccess.setPosition( pos[ d ] + kernelPos[ d ] - kernelCenter[ d ], d );
				}

				// Accumulate: image[pos + offset] * kernel[offset]
				sum += inAccess.get().getRealDouble() * kernelCursor.get().getRealDouble();
			}

			// Write result
			outCursor.get().setReal( sum );
		}

		// Copy result back to input image (in-place behavior)
		final Cursor< T > resultCursor = output.cursor();
		final Cursor< T > imageCursor = Views.iterable( image ).cursor();

		while ( resultCursor.hasNext() )
		{
			imageCursor.fwd();
			resultCursor.fwd();
			imageCursor.get().set( resultCursor.get() );
		}
	}
}
