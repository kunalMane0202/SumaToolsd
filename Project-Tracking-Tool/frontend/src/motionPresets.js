export const freestyleSpring = {
  type: "spring",
  stiffness: 120,
  damping: 14,
  mass: 1.2,
};

export const freestylePageVariants = {
  hidden: { opacity: 0, x: 18, y: 22, scale: 0.98, rotate: -1 },
  visible: {
    opacity: 1,
    x: 0,
    y: 0,
    scale: 1,
    rotate: 0,
    transition: freestyleSpring,
  },
  exit: {
    opacity: 0,
    x: -14,
    y: 18,
    scale: 0.985,
    rotate: -1,
    transition: { duration: 0.2, ease: [0.22, 1, 0.36, 1] },
  },
};

export const freestyleStaggerVariants = {
  hidden: { opacity: 1 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.08, delayChildren: 0.04 },
  },
};

export const freestyleRevealVariants = {
  hidden: { opacity: 0, y: 16, x: 6, rotate: -0.5 },
  visible: {
    opacity: 1,
    y: 0,
    x: 0,
    rotate: 0,
    transition: freestyleSpring,
  },
};

export const freestyleFormVariants = {
  hidden: { opacity: 0, y: 18, scale: 0.98 },
  visible: {
    opacity: 1,
    y: 0,
    scale: 1,
    transition: {
      ...freestyleSpring,
      staggerChildren: 0.07,
      delayChildren: 0.06,
    },
  },
};

export const freestyleInteractiveMotion = {
  whileHover: { y: -2, scale: 1.02 },
  whileTap: { scale: 0.97 },
  transition: freestyleSpring,
};
