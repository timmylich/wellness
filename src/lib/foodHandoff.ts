/**
 * Hands a just-created food from the editor back to the food picker, which opens the amount
 * sheet for it when it regains focus (one-shot).
 */
import type { Food } from '../store/types';

let pending: Food | null = null;

export function handOffFood(f: Food) {
  pending = f;
}

export function takeHandedOffFood(): Food | null {
  const f = pending;
  pending = null;
  return f;
}
