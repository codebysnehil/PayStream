import { Pipe, PipeTransform } from '@angular/core';

/** First block of a UUID: enough to recognise a payment at a glance. */
@Pipe({ name: 'shortId' })
export class ShortIdPipe implements PipeTransform {
  transform(id: string | null | undefined): string {
    return id ? id.slice(0, 8) : '';
  }
}

/** "GB82PAYS10000000000001" -> "GB82 ···· 0001" */
@Pipe({ name: 'maskIban' })
export class MaskIbanPipe implements PipeTransform {
  transform(iban: string | null | undefined): string {
    return iban && iban.length > 8 ? `${iban.slice(0, 4)} ···· ${iban.slice(-4)}` : (iban ?? '');
  }
}

/** Upper-snake reason codes to readable text: "INSUFFICIENT_FUNDS" -> "Insufficient funds". */
@Pipe({ name: 'reason' })
export class ReasonPipe implements PipeTransform {
  transform(code: string | null | undefined): string {
    if (!code) {
      return '';
    }
    const text = code.toLowerCase().replace(/_/g, ' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
  }
}
