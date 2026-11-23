import { useState } from 'react'

// One set of input rules for every form in Suma Tracking.
// The backend checks the same rules again, so a wrong value can never be saved
// even if someone skips the screen.

const EMAIL = /^(?!\.)(?!.*\.\.)[A-Za-z0-9._%+-]{1,64}(?<!\.)@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,24}$/
const PHONE = /^[1-9][0-9]{9}$/
const PERSON_NAME = /^[A-Za-z][A-Za-z .'-]*[A-Za-z.]$/
const ORG_NAME = /^[A-Za-z0-9][A-Za-z0-9 &.,'()/-]*$/
const TITLE = /^[A-Za-z][A-Za-z0-9 &.,'()/-]*$/
const EMP_CODE = /^[A-Za-z0-9-]{1,30}$/
const TOOL = /^[A-Za-z0-9 _-]*$/

const text = (v) => (v == null ? '' : String(v).trim())

/** Each rule returns an error message, or '' when the value is fine. */
export const rules = {
  required: (label) => (v) => (text(v) ? '' : `${label} is required.`),

  email: (label = 'Email ID', required = false) => (v) => {
    const s = text(v)
    if (!s) return required ? `${label} is required.` : ''
    if (s.length > 254) return `${label} is too long (maximum 254 characters).`
    if (!s.includes('@')) return `Enter a valid ${label.toLowerCase()} with an "@", for example name@company.com.`
    if (!EMAIL.test(s)) return `"${s}" is not a valid email address. Use the format name@company.com.`
    return ''
  },

  phone: (label = 'Contact number', required = false) => (v) => {
    const s = text(v)
    if (!s) return required ? `${label} is required.` : ''
    if (!/^[0-9]+$/.test(s)) return `${label} can contain digits only (no spaces, +91, dashes or letters).`
    if (s.length !== 10) return `${label} must be exactly 10 digits. You entered ${s.length}.`
    if (!PHONE.test(s)) return `${label} cannot start with 0.`
    return ''
  },

  personName: (label = 'Name', required = true) => (v) => {
    const s = text(v)
    if (!s) return required ? `${label} is required.` : ''
    if (s.length < 2) return `${label} must have at least 2 letters.`
    if (s.length > 150) return `${label} is too long (maximum 150 characters).`
    if (!PERSON_NAME.test(s)) return `${label} can contain letters, spaces, dot ( . ), apostrophe ( ' ) and hyphen ( - ) only.`
    return ''
  },

  orgName: (label = 'Name', required = true, max = 150) => (v) => {
    const s = text(v)
    if (!s) return required ? `${label} is required.` : ''
    if (s.length < 2) return `${label} must have at least 2 characters.`
    if (s.length > max) return `${label} is too long (maximum ${max} characters).`
    if (!/[A-Za-z]/.test(s)) return `${label} must contain at least one letter.`
    if (!ORG_NAME.test(s)) return `${label} can contain letters, numbers, spaces and & . , ' ( ) / - only.`
    return ''
  },

  title: (label = 'Designation', required = false, max = 100) => (v) => {
    const s = text(v)
    if (!s) return required ? `${label} is required.` : ''
    if (s.length < 2) return `${label} must have at least 2 characters.`
    if (s.length > max) return `${label} is too long (maximum ${max} characters).`
    if (!TITLE.test(s)) return `${label} must start with a letter and can contain letters, numbers, spaces and & . , ' ( ) / - only.`
    return ''
  },

  empCode: (label = 'Employee code') => (v) => {
    const s = text(v)
    if (!s) return `${label} is required.`
    if (!EMP_CODE.test(s)) return `${label} can contain letters, numbers and hyphen only, up to 30 characters (for example 1001).`
    return ''
  },

  tool: (label = 'Ticketing tool') => (v) => {
    const s = text(v)
    if (s.length > 50) return `${label} is too long (maximum 50 characters).`
    if (!TOOL.test(s)) return `${label} can contain letters, numbers, space, _ and - only (for example ZENDESK).`
    return ''
  },

  maxLen: (label, max) => (v) => (text(v).length > max ? `${label} is too long (maximum ${max} characters).` : ''),

  dateOrder: (startLabel, endLabel, start) => (end) =>
    (start && end && end < start ? `${endLabel} cannot be before ${startLabel.toLowerCase()}.` : ''),
}

/**
 * Runs the rules over a form. `schema` is { fieldName: [rule, rule...] }.
 * Returns { fieldName: message } with only the fields that have a problem.
 */
export function validate(values, schema) {
  const errors = {}
  Object.entries(schema).forEach(([field, list]) => {
    for (const rule of list) {
      const msg = rule(values[field], values)
      if (msg) { errors[field] = msg; break }
    }
  })
  return errors
}

/** Keeps only digits and at most 10 of them, so the phone box cannot take letters. */
export function digitsOnly(value, max = 10) {
  return String(value || '').replace(/[^0-9]/g, '').slice(0, max)
}

export const FIX_MESSAGE = 'Please correct the fields marked in red and try again.'

/**
 * Form state with validation. `schema` is an object, or a function of the
 * current values (for rules that depend on another field).
 * - an error appears when the user leaves a field (onBlur) or presses Add/Update
 * - once shown, it updates while the user types, and disappears when fixed
 */
export function useValidatedForm(initial, schema) {
  const [values, setValues] = useState(initial)
  const [errors, setErrors] = useState({})
  const schemaFor = (vals) => (typeof schema === 'function' ? schema(vals) : schema)
  const check = (k, vals) => {
    const s = schemaFor(vals)
    return s[k] ? (validate(vals, { [k]: s[k] })[k] || '') : ''
  }
  function setValue(k, v) {
    const next = { ...values, [k]: v }
    setValues(next)
    if (errors[k]) setErrors({ ...errors, [k]: check(k, next) })
  }
  const blur = (k) => () => setErrors((e) => ({ ...e, [k]: check(k, values) }))
  function validateAll() {
    const e = validate(values, schemaFor(values))
    setErrors(e)
    return Object.keys(e).length === 0
  }
  return { values, setValues, errors, setErrors, setValue, blur, validateAll }
}