import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiError } from '../api/models';

export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        const body = err.error as Partial<ApiError> | undefined;
        const userMessage =
          body?.details?.length
            ? body.details.join(' · ')
            : (body?.message ?? 'The request could not be completed. Please try again.');
        return throwError(() => Object.assign(Object.create(err), { userMessage }));
      }
      return throwError(() => err);
    }),
  );
