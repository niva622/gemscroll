"""A simple command-line calculator."""

def add(a, b):
    """Return the sum of a and b."""
    return a + b


def subtract(a, b):
    """Return the difference of a and b."""
    return a - b


def multiply(a, b):
    """Return the product of a and b."""
    return a * b


def divide(a, b):
    """Return the quotient of a and b."""
    if b == 0:
        raise ZeroDivisionError("Cannot divide by zero")
    return a / b


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Simple calculator")
    parser.add_argument("a", type=float, help="First operand")
    parser.add_argument("op", choices=['+', '-', '*', '/'], help="Operation")
    parser.add_argument("b", type=float, help="Second operand")
    args = parser.parse_args()

    operations = {
        '+': add,
        '-': subtract,
        '*': multiply,
        '/': divide,
    }
    result = operations[args.op](args.a, args.b)
    print(result)


if __name__ == "__main__":
    main()
