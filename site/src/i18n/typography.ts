/**
 * French typography: a non-breaking space before « : » and after « «, and a narrow one before
 * ; ? ! and », so punctuation never wraps onto a line of its own.
 */
export function frenchSpacing(text: string): string {
  return text
    .replace(/ ([:»])/g, ' $1')
    .replace(/« /g, '« ')
    .replace(/ ([;?!])/g, ' $1');
}
